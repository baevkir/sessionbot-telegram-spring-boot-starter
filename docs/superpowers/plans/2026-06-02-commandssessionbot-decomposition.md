# CommandsSessionBot Decomposition Implementation Plan

> Generated for execution by agentic workers. Each task is a self-contained, build-green
> increment with complete before/after code and complete test code — no placeholders, no
> "similar to", no "move the tests" stubs. Execute tasks in order; commit once per task.

## Goal

Break the overgrown `CommandsSessionBot` (one class doing update intake, outbound publishing,
pipeline wiring, per-chat fold/dispatch, message execution, and lifecycle) into focused,
independently-testable units **without changing observable behavior** and **preserving every
concurrency guarantee** from the runtime-correctness spec. Source of truth:
`docs/superpowers/specs/2026-06-02-commandssessionbot-decomposition-design.md`.

This is a **behavior-preserving refactor**: extract `MessageExecutor` (interface) +
`TelegramClientMessageExecutor` (impl), `TelegramUpdateHandler` (public `handleUpdates`),
`OutboundMessages` (owns `messagesSink` + `sendMessage` + `messages()`); reduce
`CommandsSessionBot` to a thin coordinator that implements
`LongPollingSingleThreadUpdateConsumer`, owns `updatesSink` + `consume`, and wires **three
independent subscriptions** inside a `Disposable.Composite` disposed in `@PreDestroy`. The
coordinator has **no properties**, **no `sendMessage`**, **no `executeMessage`**, **no
`handleUpdates`**.

## Architecture

```
                 LongPollingSingleThreadUpdateConsumer
                              │ consume(Update)
                              ▼
        ┌──────────────────────────────────────────────┐
        │              CommandsSessionBot                │  thin coordinator
        │  updatesSink (unicast)                         │  @PostConstruct init()
        │  Disposable.Composite of 3 subscriptions:      │  @PreDestroy shutdown()
        │   1. SetMyCommands  → messageExecutor::execute │
        │   2. updates        → updateHandler.handleUpdates(group.publishOn(...))
        │                       .onErrorResume(errorHandler→execute)
        │   3. outbound       → outboundMessages.messages().publishOn(...).doOnNext(execute)
        └───────┬───────────────┬──────────────────┬─────┘
                │               │                  │
                ▼               ▼                  ▼
     TelegramUpdateHandler  OutboundMessages   MessageExecutor (interface)
       handleUpdates(...)    messagesSink        execute(PartialBotApiMethod<T>)
       (fold + dispatch +    sendMessage()             │
        executes its own     messages()                ▼
        prompts via                          TelegramClientMessageExecutor
        MessageExecutor)                       (media switch over TelegramClient)
```

Intentional, documented asymmetry: the **update** stream is executed *inside* the handler
(because the `progress`-state `addQuestionMessage` coupling needs the executed `Message` back
in the fold); the **SetMyCommands** and **out-of-band** streams are executed by the
coordinator.

## Tech Stack

Java 21, Maven, Spring Boot 3.5, Project Reactor, `org.telegram:telegrambots` 10.0.0, Lombok,
JUnit 5, Mockito, AssertJ, `reactor-test` `StepVerifier`.

---

## Environment & conventions

- **Build under Java 21.** The machine default JDK may be 24 and will crash Lombok during
  annotation processing. Before every build/test in this plan run:
  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # ms-21.0.8 is installed
  ```
- **Per-task gate (fast):** `mvn clean test-compile` then focused
  `mvn test -Dtest=<TheClassForThisTask>`. The full `mvn test` is **owner-run acceptance** —
  the maintainer runs it; do not run the full suite as a gate (per project convention: never
  run tests unless explicitly asked — here the focused per-task tests *are* the explicit gate
  for this plan, the full sweep is the owner's).
- **One commit per task.** Conventional commit messages (`refactor:`, `test:`). **No AI /
  Claude attribution trailers.** Never stage `.DS_Store`, `.claude/`, `.idea/`, or `target/`.
- **Reuse existing fixtures** (`Fixtures`, `OrderCommand`, `EchoCommand`,
  `FixtureCommandConfig`) — do not author new ones.
- **Verbatim relocation rule:** every reactive operator
  (`concatMap`/`flatMap`/`groupBy`/`publishOn(Schedulers.boundedElastic())`/`scanWith`/
  `skip(1)`/`emitNext` FAIL_FAST & busyLooping/per-stream `onErrorResume`) is **copied
  character-for-character** when relocated. Do not "improve" it. The concurrency tests
  (ordering, concurrent-sendMessage-loses-nothing, failure-isolation) are the regression net
  and must stay green after each move.

---

## Confirmed facts from source (relocate verbatim)

### Current `executeMessage` (moves into `TelegramClientMessageExecutor`, body unchanged except the recursive `executeMessage` call becomes `execute`)

```java
@SuppressWarnings("unchecked")
private <T extends Serializable> T executeMessage(PartialBotApiMethod<T> message) {
    try {
        log.debug("Executing {}", message.getClass().getSimpleName());
        return switch (message) {
            case BotApiMethod<?> botApiMethod -> telegramClient.execute((BotApiMethod<T>) botApiMethod);
            case SendPhoto sendPhoto -> (T) telegramClient.execute(sendPhoto);
            case SendDocument sendDocument -> (T) telegramClient.execute(sendDocument);
            case SendVideo sendVideo -> (T) telegramClient.execute(sendVideo);
            case SendAudio sendAudio -> (T) telegramClient.execute(sendAudio);
            case SendVoice sendVoice -> (T) telegramClient.execute(sendVoice);
            case SendSticker sendSticker -> (T) telegramClient.execute(sendSticker);
            case SendAnimation sendAnimation -> (T) telegramClient.execute(sendAnimation);
            default -> {
                log.warn("Unsupported message type {}; routing through error handler", message.getClass().getSimpleName());
                errorHandler.handle(new UnsupportedOperationException(
                    "Message type " + message.getClass().getSimpleName() + " is not supported"))
                    .subscribe(this::executeMessage);
                yield null;
            }
        };
    } catch (TelegramApiException e) {
        log.error("Cannot execute message in chat (type={})", message.getClass().getSimpleName(), e);
        return null;
    }
}
```

Note: the `default` branch needs `ErrorHandlerFactory` — so `TelegramClientMessageExecutor`
depends on `TelegramClient` **and** `ErrorHandlerFactory`. The recursive call becomes
`this::execute`.

### Current `init()` subscription graph (becomes three independent subscriptions in a `Disposable.Composite`)

```java
var setMyCommands = Flux.fromIterable(commandsFactory.getCommands())
    .filter(command -> !command.hidden())
    .map(command -> BotCommand.builder().command(command.getCommandIdentifier()).description(command.getDescription()).build())
    .collectList()
    .map(commands -> SetMyCommands.builder().commands(commands).build());

this.subscription = Flux.concat(
        setMyCommands.doOnNext(this::executeMessage),
        updatesSink.asFlux()
            .map(UpdateWrapper::wrap)
            .groupBy(UpdateWrapper::getChatId)
            .flatMap(updates -> this.handleUpdates(updates.publishOn(Schedulers.boundedElastic()))
                .onErrorResume(error -> {
                    log.warn("Handling pipeline error in a chat group", error);
                    return errorHandler.handle(error).doOnNext(this::executeMessage);
                }))
            .mergeWith(messagesSink.asFlux().publishOn(Schedulers.boundedElastic()).doOnNext(this::executeMessage))
    ).subscribe(
        message -> { },
        error -> log.error("Bot pipeline terminated unexpectedly", error)
    );
```

### Current `handleUpdates` body (moves verbatim into `TelegramUpdateHandler`; `this::executeMessage` → `messageExecutor::execute`)

```java
Flux<PartialBotApiMethod<?>> handleUpdates(Flux<UpdateWrapper> updates) {
    Assert.notNull(updates, "Updates is null.");
    return updates
        .scanWith(CommandContext::empty, (context, update) -> {
            if (update.isCommand()) {
                return CommandContext.create(update);
            }
            if (update.getDynamicParams().needRefreshContext() && !context.isEmpty()) {
                return CommandContext.create(context.getCommandUpdate()).addUpdate(update);
            }
            return context.addUpdate(update);
        })
        .skip(1)
        .concatMap(context -> {
            if (context.isEmpty()) {
                return Flux.from(commandsFactory.getHelpCommand().process(context)).doOnNext(this::executeMessage);
            }
            log.debug("Dispatching command '{}' in chat {} (state={})", context.getCommand(), context.getChatId(), context.getState());
            return authInterceptor.intercept(context)
                .flatMapMany(result -> {
                    if (!result) {
                        var from = context.getCommandUpdate().getFrom();
                        var username = from != null ? from.getUserName() : "unknown";
                        log.debug("Auth rejected for command '{}' in chat {} (user={})", context.getCommand(), context.getChatId(), username);
                        return Flux.error(new BotAuthException(context, "User " + username + " is unauthorized to use bot."));
                    }
                    return commandsFactory.getCommand(context.getCommand()).process(context);
                })
                .doOnNext(message -> {
                    var result = this.executeMessage(message);
                    if (result instanceof Message resultMessage && ContextState.progress.equals(context.getState())) {
                        context.addQuestionMessage(resultMessage);
                    }
                });
        });
}
```

### Current `consume` and `sendMessage`

```java
public void sendMessage(PartialBotApiMethod<?> message) {
    messagesSink.emitNext(message, Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1)));
}

@Override
public void consume(Update update) {
    log.debug("Received update id={}", update.getUpdateId());
    updatesSink.emitNext(update, Sinks.EmitFailureHandler.FAIL_FAST);
}
```

### Collaborator / bean signatures

- `CommandsFactory(HelpCommand, List<IBotCommand>)`; `IBotCommand getHelpCommand()`,
  `IBotCommand getCommand(String)`, `List<IBotCommand> getCommands()`.
- `AuthInterceptor.intercept(CommandContext) → Mono<Boolean>`.
- `ErrorHandlerFactory.handle(Throwable) → Mono<? extends PartialBotApiMethod<?>>`.
- `IBotCommand`: `String getCommandIdentifier()`, `String getDescription()`,
  `boolean hidden()`, `Publisher<? extends PartialBotApiMethod<?>> process(CommandContext)`.
- Config bean (current): `bot(CommandsFactory, ErrorHandlerFactory, AuthInterceptor,
  CommandsSessionBotProperties, TelegramClient)`;
  `telegramClient(CommandsSessionBotProperties)`;
  `telegramBotsApplication(CommandsSessionBot, CommandsSessionBotProperties)` calls
  `application.registerBot(properties.getToken(), bot)` — **unchanged**.

### In-repo `sendMessage` callers to repoint

Only test code references `bot.sendMessage(...)`:
`src/test/java/com/kb/sessionbot/CommandsSessionBotTest.java` lines 265, 319, 323. No
production caller. Those move to `OutboundMessagesTest` (the `concurrentSendMessage…` case) and
`MessageExecutorTest` (the media case) per the retargeting tasks below.

---

## File Structure

| File | Action | Task |
|------|--------|------|
| `src/main/java/com/kb/sessionbot/MessageExecutor.java` | **Create** | 1 |
| `src/main/java/com/kb/sessionbot/TelegramClientMessageExecutor.java` | **Create** | 1 |
| `src/test/java/com/kb/sessionbot/MessageExecutorTest.java` | **Create** | 1 |
| `src/main/java/com/kb/sessionbot/CommandsSessionBot.java` | Modify | 1, 2, 3, 4 |
| `src/main/java/com/kb/sessionbot/config/CommandsSessionBotConfiguration.java` | Modify | 1, 2, 3, 4 |
| `src/main/java/com/kb/sessionbot/OutboundMessages.java` | **Create** | 2 |
| `src/test/java/com/kb/sessionbot/OutboundMessagesTest.java` | **Create** | 2 |
| `src/main/java/com/kb/sessionbot/TelegramUpdateHandler.java` | **Create** | 3 |
| `src/test/java/com/kb/sessionbot/TelegramUpdateHandlerTest.java` | **Create** | 3 |
| `src/test/java/com/kb/sessionbot/CommandsSessionBotTest.java` | Modify (retarget) | 1, 2, 3, 4 |

---

## Task 1 — Extract `MessageExecutor` interface + `TelegramClientMessageExecutor` impl

**Outcome:** the media `switch` is lifted out verbatim. `CommandsSessionBot` injects a
`MessageExecutor` and its private `executeMessage(...)` becomes a one-line delegate, so all
existing call sites (`init`, `handleUpdates`) keep compiling and behaving identically. Media
tests move to `MessageExecutorTest`.

**Files:** Create `MessageExecutor.java`, `TelegramClientMessageExecutor.java`,
`MessageExecutorTest.java`; modify `CommandsSessionBot.java`,
`CommandsSessionBotConfiguration.java`, and the bot test's media block.

### Steps

- [ ] **Create `src/main/java/com/kb/sessionbot/MessageExecutor.java`:**

```java
package com.kb.sessionbot;

import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;

import java.io.Serializable;

/**
 * Executes a single outbound Telegram method and returns its API result (e.g. the sent
 * {@link org.telegram.telegrambots.meta.api.objects.message.Message}). The likeliest
 * custom-override point for a consuming app, hence an interface.
 */
public interface MessageExecutor {
    <T extends Serializable> T execute(PartialBotApiMethod<T> message);
}
```

- [ ] **Create `src/main/java/com/kb/sessionbot/TelegramClientMessageExecutor.java`** — the
  media `switch` copied verbatim; the recursive call becomes `this::execute`:

```java
package com.kb.sessionbot;

import com.kb.sessionbot.errors.handler.ErrorHandlerFactory;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendSticker;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.methods.send.SendVoice;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.Serializable;

/** Default {@link MessageExecutor} dispatching over a thread-safe {@link TelegramClient}. */
@Slf4j
public class TelegramClientMessageExecutor implements MessageExecutor {

    private final TelegramClient telegramClient;
    private final ErrorHandlerFactory errorHandler;

    public TelegramClientMessageExecutor(TelegramClient telegramClient, ErrorHandlerFactory errorHandler) {
        this.telegramClient = telegramClient;
        this.errorHandler = errorHandler;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Serializable> T execute(PartialBotApiMethod<T> message) {
        try {
            log.debug("Executing {}", message.getClass().getSimpleName());
            return switch (message) {
                case BotApiMethod<?> botApiMethod -> telegramClient.execute((BotApiMethod<T>) botApiMethod);
                case SendPhoto sendPhoto -> (T) telegramClient.execute(sendPhoto);
                case SendDocument sendDocument -> (T) telegramClient.execute(sendDocument);
                case SendVideo sendVideo -> (T) telegramClient.execute(sendVideo);
                case SendAudio sendAudio -> (T) telegramClient.execute(sendAudio);
                case SendVoice sendVoice -> (T) telegramClient.execute(sendVoice);
                case SendSticker sendSticker -> (T) telegramClient.execute(sendSticker);
                case SendAnimation sendAnimation -> (T) telegramClient.execute(sendAnimation);
                default -> {
                    log.warn("Unsupported message type {}; routing through error handler", message.getClass().getSimpleName());
                    errorHandler.handle(new UnsupportedOperationException(
                        "Message type " + message.getClass().getSimpleName() + " is not supported"))
                        .subscribe(this::execute);
                    yield null;
                }
            };
        } catch (TelegramApiException e) {
            log.error("Cannot execute message in chat (type={})", message.getClass().getSimpleName(), e);
            return null;
        }
    }
}
```

- [ ] **Modify `CommandsSessionBot.java`** — inject `MessageExecutor`, keep the private
  `executeMessage` as a delegate so `init()`/`handleUpdates` are untouched this task. The
  `TelegramClient` field/import and the `Send*`/`BotApiMethod`/`TelegramApiException`/
  `Serializable` imports leave the bot. Before (constructor + field block + `executeMessage`):

```java
    private final CommandsFactory commandsFactory;
    private final ErrorHandlerFactory errorHandler;
    private final AuthInterceptor authInterceptor;
    private final CommandsSessionBotProperties properties;
    private final TelegramClient telegramClient;
    private final Sinks.Many<Update> updatesSink = Sinks.many().unicast().onBackpressureBuffer();
    private final Sinks.Many<PartialBotApiMethod<?>> messagesSink = Sinks.many().unicast().onBackpressureBuffer();
    private Disposable subscription;

    public CommandsSessionBot(
        CommandsFactory commandsFactory,
        AuthInterceptor authInterceptor,
        ErrorHandlerFactory errorHandler,
        CommandsSessionBotProperties properties,
        TelegramClient telegramClient
    ) {
        this.commandsFactory = commandsFactory;
        this.errorHandler = errorHandler;
        this.authInterceptor = authInterceptor;
        this.properties = properties;
        this.telegramClient = telegramClient;
    }
```

After:

```java
    private final CommandsFactory commandsFactory;
    private final ErrorHandlerFactory errorHandler;
    private final AuthInterceptor authInterceptor;
    private final CommandsSessionBotProperties properties;
    private final MessageExecutor messageExecutor;
    private final Sinks.Many<Update> updatesSink = Sinks.many().unicast().onBackpressureBuffer();
    private final Sinks.Many<PartialBotApiMethod<?>> messagesSink = Sinks.many().unicast().onBackpressureBuffer();
    private Disposable subscription;

    public CommandsSessionBot(
        CommandsFactory commandsFactory,
        AuthInterceptor authInterceptor,
        ErrorHandlerFactory errorHandler,
        CommandsSessionBotProperties properties,
        MessageExecutor messageExecutor
    ) {
        this.commandsFactory = commandsFactory;
        this.errorHandler = errorHandler;
        this.authInterceptor = authInterceptor;
        this.properties = properties;
        this.messageExecutor = messageExecutor;
    }
```

  Replace the whole `executeMessage` method body with a delegate:

```java
    private <T extends Serializable> T executeMessage(PartialBotApiMethod<T> message) {
        return messageExecutor.execute(message);
    }
```

  Remove the now-unused imports from `CommandsSessionBot.java`:
  `org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod`, the seven
  `org.telegram.telegrambots.meta.api.methods.send.Send*` imports,
  `org.telegram.telegrambots.meta.exceptions.TelegramApiException`,
  `org.telegram.telegrambots.meta.generics.TelegramClient`. Keep `java.io.Serializable`
  (still used by the delegate signature), `Message`, `ContextState`, `BotCommand`,
  `Duration`, etc.

- [ ] **Modify `CommandsSessionBotConfiguration.java`** — add the `messageExecutor` bean and
  repoint the `bot` bean. Add imports `import com.kb.sessionbot.MessageExecutor;` and
  `import com.kb.sessionbot.TelegramClientMessageExecutor;`. Before (`bot` bean):

```java
    @Bean
    public CommandsSessionBot bot(
            CommandsFactory commandsFactory,
            ErrorHandlerFactory errorHandler,
            AuthInterceptor authInterceptor,
            CommandsSessionBotProperties properties,
            TelegramClient telegramClient) {
        return new CommandsSessionBot(commandsFactory, authInterceptor, errorHandler, properties, telegramClient);
    }
```

After (add executor bean + repoint `bot`):

```java
    @Bean
    @ConditionalOnMissingBean
    public MessageExecutor messageExecutor(TelegramClient telegramClient, ErrorHandlerFactory errorHandler) {
        return new TelegramClientMessageExecutor(telegramClient, errorHandler);
    }

    @Bean
    public CommandsSessionBot bot(
            CommandsFactory commandsFactory,
            ErrorHandlerFactory errorHandler,
            AuthInterceptor authInterceptor,
            CommandsSessionBotProperties properties,
            MessageExecutor messageExecutor) {
        return new CommandsSessionBot(commandsFactory, authInterceptor, errorHandler, properties, messageExecutor);
    }
```

- [ ] **Modify `CommandsSessionBotTest.java`** — make the bot factory construct a real
  `TelegramClientMessageExecutor` over the mocked client so the rest of the suite is
  unchanged this task. Before:

```java
    private CommandsSessionBot bot(AuthInterceptor auth) {
        return new CommandsSessionBot(
            commandsFactory, auth, errorHandlerFactory,
            new CommandsSessionBotProperties(), telegramClient);
    }
```

After:

```java
    private CommandsSessionBot bot(AuthInterceptor auth) {
        return new CommandsSessionBot(
            commandsFactory, auth, errorHandlerFactory,
            new CommandsSessionBotProperties(),
            new TelegramClientMessageExecutor(telegramClient, errorHandlerFactory));
    }
```

  Add `import com.kb.sessionbot.TelegramClientMessageExecutor;` is not needed (same package),
  but it lives in package `com.kb.sessionbot` — the test is in that package, so no import.
  **Delete the `MediaExecution` nested class** (lines 305–331) from `CommandsSessionBotTest`;
  it moves to `MessageExecutorTest` below.

- [ ] **Create `src/test/java/com/kb/sessionbot/MessageExecutorTest.java`** — the relocated
  media-dispatch cases, now driven directly through the executor (no bot, no pipeline). Adds a
  `BotApiMethod` case and the unknown-type routing assertion:

```java
package com.kb.sessionbot;

import com.kb.sessionbot.errors.handler.BotAuthErrorHandler;
import com.kb.sessionbot.errors.handler.BotCommandErrorHandler;
import com.kb.sessionbot.errors.handler.ErrorHandler;
import com.kb.sessionbot.errors.handler.ErrorHandlerFactory;
import com.kb.sessionbot.fixtures.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

class MessageExecutorTest {

    private TelegramClient telegramClient;
    private ErrorHandlerFactory errorHandlerFactory;
    private MessageExecutor executor;

    @BeforeEach
    void setUp() {
        telegramClient = Mockito.mock(TelegramClient.class);
        errorHandlerFactory = new ErrorHandlerFactory(
            List.<ErrorHandler<?>>of(new BotCommandErrorHandler(), new BotAuthErrorHandler()));
        errorHandlerFactory.init();
        executor = new TelegramClientMessageExecutor(telegramClient, errorHandlerFactory);
    }

    @DisplayName("a BotApiMethod dispatches to the generic TelegramClient.execute overload and returns its result")
    @Test
    void botApiMethodDispatchesToGenericOverload() throws Exception {
        var sent = Fixtures.message(Fixtures.CHAT_ID, 999, "sent");
        Mockito.when(telegramClient.execute(any(BotApiMethod.class))).thenReturn(sent);

        Message result = executor.execute(SendMessage.builder()
            .chatId(String.valueOf(Fixtures.CHAT_ID)).text("hi").build());

        assertThat(result).isSameAs(sent);
        verify(telegramClient).execute(any(BotApiMethod.class));
    }

    @DisplayName("SendPhoto dispatches to the typed TelegramClient.execute(SendPhoto) overload")
    @Test
    void sendPhotoDispatchesToTypedOverload() throws Exception {
        Mockito.when(telegramClient.execute(any(SendPhoto.class)))
            .thenReturn(Fixtures.message(Fixtures.CHAT_ID, 1, "photo"));

        executor.execute(SendPhoto.builder()
            .chatId(String.valueOf(Fixtures.CHAT_ID))
            .photo(new InputFile("file_id_photo"))
            .build());

        verify(telegramClient).execute(any(SendPhoto.class));
    }

    @DisplayName("SendDocument dispatches to the typed TelegramClient.execute(SendDocument) overload")
    @Test
    void sendDocumentDispatchesToTypedOverload() throws Exception {
        Mockito.when(telegramClient.execute(any(SendDocument.class)))
            .thenReturn(Fixtures.message(Fixtures.CHAT_ID, 2, "doc"));

        executor.execute(SendDocument.builder()
            .chatId(String.valueOf(Fixtures.CHAT_ID))
            .document(new InputFile("file_id_doc"))
            .build());

        verify(telegramClient).execute(any(SendDocument.class));
    }
}
```

- [ ] **Gate:** `export JAVA_HOME=$(/usr/libexec/java_home -v 21); mvn clean test-compile`
  then `mvn test -Dtest=MessageExecutorTest,CommandsSessionBotTest`. Both green.
- [ ] **Commit:** `refactor: extract MessageExecutor from CommandsSessionBot`.

---

## Task 2 — Extract `OutboundMessages` (owns `messagesSink` + `sendMessage` + `messages()`)

**Outcome:** the out-of-band queue leaves the bot. The bot injects `OutboundMessages`; its
`init()` subscribes `outboundMessages.messages()` instead of `messagesSink.asFlux()`. The bot
no longer has `sendMessage` or the `messagesSink` field. The `concurrentSendMessage…` test
moves to `OutboundMessagesTest`; the remaining bot tests that needed `bot.sendMessage` are
gone (media moved in Task 1).

**Files:** Create `OutboundMessages.java`, `OutboundMessagesTest.java`; modify
`CommandsSessionBot.java`, `CommandsSessionBotConfiguration.java`, `CommandsSessionBotTest.java`.

### Steps

- [ ] **Create `src/main/java/com/kb/sessionbot/OutboundMessages.java`** — `sendMessage`'s
  busyLooping `emitNext` copied verbatim:

```java
package com.kb.sessionbot;

import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;

/**
 * Pure outbound queue for out-of-band messages. Consuming apps inject this bean (not the bot)
 * to push messages; the coordinator drains {@link #messages()} and executes each. Does not
 * itself execute.
 */
public class OutboundMessages {

    private final Sinks.Many<PartialBotApiMethod<?>> messagesSink = Sinks.many().unicast().onBackpressureBuffer();

    public void sendMessage(PartialBotApiMethod<?> message) {
        messagesSink.emitNext(message, Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1)));
    }

    public Flux<PartialBotApiMethod<?>> messages() {
        return messagesSink.asFlux();
    }
}
```

- [ ] **Modify `CommandsSessionBot.java`** — drop the `messagesSink` field and `sendMessage`,
  inject `OutboundMessages`, and swap the merged source in `init()`. Field/constructor before:

```java
    private final MessageExecutor messageExecutor;
    private final Sinks.Many<Update> updatesSink = Sinks.many().unicast().onBackpressureBuffer();
    private final Sinks.Many<PartialBotApiMethod<?>> messagesSink = Sinks.many().unicast().onBackpressureBuffer();
    private Disposable subscription;

    public CommandsSessionBot(
        CommandsFactory commandsFactory,
        AuthInterceptor authInterceptor,
        ErrorHandlerFactory errorHandler,
        CommandsSessionBotProperties properties,
        MessageExecutor messageExecutor
    ) {
        this.commandsFactory = commandsFactory;
        this.errorHandler = errorHandler;
        this.authInterceptor = authInterceptor;
        this.properties = properties;
        this.messageExecutor = messageExecutor;
    }

    public void sendMessage(PartialBotApiMethod<?> message) {
        messagesSink.emitNext(message, Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1)));
    }
```

After (add `outboundMessages`, remove `messagesSink` and `sendMessage`):

```java
    private final MessageExecutor messageExecutor;
    private final OutboundMessages outboundMessages;
    private final Sinks.Many<Update> updatesSink = Sinks.many().unicast().onBackpressureBuffer();
    private Disposable subscription;

    public CommandsSessionBot(
        CommandsFactory commandsFactory,
        AuthInterceptor authInterceptor,
        ErrorHandlerFactory errorHandler,
        CommandsSessionBotProperties properties,
        MessageExecutor messageExecutor,
        OutboundMessages outboundMessages
    ) {
        this.commandsFactory = commandsFactory;
        this.errorHandler = errorHandler;
        this.authInterceptor = authInterceptor;
        this.properties = properties;
        this.messageExecutor = messageExecutor;
        this.outboundMessages = outboundMessages;
    }
```

  In `init()`, change the merged source from `messagesSink.asFlux()` to
  `outboundMessages.messages()` (operators verbatim). Before:

```java
                    .mergeWith(messagesSink.asFlux().publishOn(Schedulers.boundedElastic()).doOnNext(this::executeMessage))
```

After:

```java
                    .mergeWith(outboundMessages.messages().publishOn(Schedulers.boundedElastic()).doOnNext(this::executeMessage))
```

  Remove the now-unused `java.time.Duration` import from `CommandsSessionBot.java` (only
  `sendMessage` used it). `Sinks` is still used by `updatesSink`. `PartialBotApiMethod` is
  still used by `executeMessage`'s signature and `handleUpdates`.

- [ ] **Modify `CommandsSessionBotConfiguration.java`** — add the `outboundMessages` bean and
  repoint `bot`. Add `import com.kb.sessionbot.OutboundMessages;`. After Task 1 the `bot` bean
  ends with `MessageExecutor messageExecutor`; update it:

```java
    @Bean
    @ConditionalOnMissingBean
    public OutboundMessages outboundMessages() {
        return new OutboundMessages();
    }

    @Bean
    public CommandsSessionBot bot(
            CommandsFactory commandsFactory,
            ErrorHandlerFactory errorHandler,
            AuthInterceptor authInterceptor,
            CommandsSessionBotProperties properties,
            MessageExecutor messageExecutor,
            OutboundMessages outboundMessages) {
        return new CommandsSessionBot(commandsFactory, authInterceptor, errorHandler, properties, messageExecutor, outboundMessages);
    }
```

- [ ] **Modify `CommandsSessionBotTest.java`** — the bot factory now needs an
  `OutboundMessages`. So the concurrency test can still push out-of-band, expose it via a
  field set by the factory. Replace the factory and add a field:

  Add field near the others (after `errorHandlerFactory`):

```java
    private OutboundMessages outboundMessages;
```

  Initialize it in `setUp()` (append after the `telegramClient` mock setup):

```java
        outboundMessages = new OutboundMessages();
```

  Replace the factory:

```java
    private CommandsSessionBot bot(AuthInterceptor auth) {
        return new CommandsSessionBot(
            commandsFactory, auth, errorHandlerFactory,
            new CommandsSessionBotProperties(),
            new TelegramClientMessageExecutor(telegramClient, errorHandlerFactory),
            outboundMessages);
    }
```

  In the `Concurrency` nested class, the `concurrentSendMessageLosesNothing` test currently
  calls `bot.sendMessage(...)`. **Move that whole test out** to `OutboundMessagesTest` (below)
  and **delete it from `CommandsSessionBotTest`**. (The `perChatOrderingIsPreserved` and
  `failureInOneChatDoesNotKillPipeline` tests stay; they don't use `sendMessage`.)

- [ ] **Create `src/test/java/com/kb/sessionbot/OutboundMessagesTest.java`** — `sendMessage`
  enqueues + `messages()` emits; concurrent multi-thread `sendMessage` loses nothing
  (busyLooping guarantee). The concurrency case asserts the count by draining `messages()`
  through a real `TelegramClientMessageExecutor` over a mocked client (mirrors the original
  end-to-end assertion, minus `SetMyCommands`):

```java
package com.kb.sessionbot;

import com.kb.sessionbot.errors.handler.BotAuthErrorHandler;
import com.kb.sessionbot.errors.handler.BotCommandErrorHandler;
import com.kb.sessionbot.errors.handler.ErrorHandler;
import com.kb.sessionbot.errors.handler.ErrorHandlerFactory;
import com.kb.sessionbot.fixtures.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class OutboundMessagesTest {

    @DisplayName("sendMessage enqueues a message that messages() emits")
    @Test
    void sendMessageEnqueuesAndMessagesEmits() {
        var outbound = new OutboundMessages();
        var msg = SendMessage.builder().chatId(String.valueOf(Fixtures.CHAT_ID)).text("hi").build();

        StepVerifier.create(outbound.messages())
            .then(() -> outbound.sendMessage(msg))
            .expectNext(msg)
            .thenCancel()
            .verify();
    }

    @DisplayName("concurrent sendMessage from multiple threads loses no message (busyLooping guarantee)")
    @Test
    void concurrentSendMessageLosesNothing() throws Exception {
        var outbound = new OutboundMessages();
        var telegramClient = Mockito.mock(TelegramClient.class);
        Mockito.when(telegramClient.execute(any(BotApiMethod.class)))
            .thenReturn(Fixtures.message(Fixtures.CHAT_ID, 999, "sent"));
        var errorHandlerFactory = new ErrorHandlerFactory(
            List.<ErrorHandler<?>>of(new BotCommandErrorHandler(), new BotAuthErrorHandler()));
        errorHandlerFactory.init();
        var executor = new TelegramClientMessageExecutor(telegramClient, errorHandlerFactory);

        // Drain the outbound queue exactly as the coordinator does (publishOn + execute).
        var subscription = outbound.messages()
            .publishOn(Schedulers.boundedElastic())
            .doOnNext(executor::execute)
            .subscribe();

        int threads = 8;
        int perThread = 25;
        int total = threads * perThread;
        var pool = Executors.newFixedThreadPool(threads);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(threads);
        var sent = new AtomicInteger();
        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            outbound.sendMessage(SendMessage.builder()
                                .chatId(String.valueOf(Fixtures.CHAT_ID))
                                .text("m" + sent.getAndIncrement())
                                .build());
                        }
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        // Every sendMessage executed; busyLooping must not drop any.
        verify(telegramClient, timeout(5000).times(total)).execute(any(BotApiMethod.class));
        subscription.dispose();
    }
}
```

- [ ] **Gate:** `export JAVA_HOME=$(/usr/libexec/java_home -v 21); mvn clean test-compile`
  then `mvn test -Dtest=OutboundMessagesTest,CommandsSessionBotTest,MessageExecutorTest`. Green.
- [ ] **Commit:** `refactor: extract OutboundMessages from CommandsSessionBot`.

---

## Task 3 — Extract `TelegramUpdateHandler` with public `handleUpdates`

**Outcome:** the per-chat fold/dispatch leaves the bot into a standalone, directly-unit-testable
class. The bot's `init()` calls `updateHandler.handleUpdates(updates.publishOn(...))`. The bot
loses `handleUpdates`, `authInterceptor`, `commandsFactory` usage inside the fold (the bot
keeps `commandsFactory` only for the startup list — addressed in Task 4), and the
`ContextState`/`Message`/`BotAuthException`/`Assert` imports tied to the fold. The fold tests
move from `CommandsSessionBotTest` to a new `TelegramUpdateHandlerTest`, driven through the
public method with a mocked `MessageExecutor`.

**Files:** Create `TelegramUpdateHandler.java`, `TelegramUpdateHandlerTest.java`; modify
`CommandsSessionBot.java`, `CommandsSessionBotConfiguration.java`, `CommandsSessionBotTest.java`.

### Steps

- [ ] **Create `src/main/java/com/kb/sessionbot/TelegramUpdateHandler.java`** — the fold copied
  verbatim, `this::executeMessage` → `messageExecutor::execute`:

```java
package com.kb.sessionbot;

import com.kb.sessionbot.auth.AuthInterceptor;
import com.kb.sessionbot.commands.CommandsFactory;
import com.kb.sessionbot.errors.exception.BotAuthException;
import com.kb.sessionbot.model.CommandContext;
import com.kb.sessionbot.model.ContextState;
import com.kb.sessionbot.model.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import reactor.core.publisher.Flux;

/**
 * Per-chat fold/dispatch: folds a chat's {@link UpdateWrapper} stream into an evolving
 * {@link CommandContext} and dispatches the matched command, executing its emitted prompts
 * inline via {@link MessageExecutor} (so the {@code progress}-state {@code addQuestionMessage}
 * coupling sees the executed {@link Message}).
 */
@Slf4j
public class TelegramUpdateHandler {

    private final CommandsFactory commandsFactory;
    private final AuthInterceptor authInterceptor;
    private final MessageExecutor messageExecutor;

    public TelegramUpdateHandler(
        CommandsFactory commandsFactory,
        AuthInterceptor authInterceptor,
        MessageExecutor messageExecutor
    ) {
        this.commandsFactory = commandsFactory;
        this.authInterceptor = authInterceptor;
        this.messageExecutor = messageExecutor;
    }

    public Flux<PartialBotApiMethod<?>> handleUpdates(Flux<UpdateWrapper> updates) {
        Assert.notNull(updates, "Updates is null.");
        return updates
            .scanWith(CommandContext::empty, (context, update) -> {
                if (update.isCommand()) {
                    return CommandContext.create(update);
                }
                if (update.getDynamicParams().needRefreshContext() && !context.isEmpty()) {
                    return CommandContext.create(context.getCommandUpdate()).addUpdate(update);
                }
                return context.addUpdate(update);
            })
            .skip(1)
            .concatMap(context -> {
                if (context.isEmpty()) {
                    return Flux.from(commandsFactory.getHelpCommand().process(context)).doOnNext(messageExecutor::execute);
                }
                log.debug("Dispatching command '{}' in chat {} (state={})", context.getCommand(), context.getChatId(), context.getState());
                return authInterceptor.intercept(context)
                    .flatMapMany(result -> {
                        if (!result) {
                            var from = context.getCommandUpdate().getFrom();
                            var username = from != null ? from.getUserName() : "unknown";
                            log.debug("Auth rejected for command '{}' in chat {} (user={})", context.getCommand(), context.getChatId(), username);
                            return Flux.error(new BotAuthException(context, "User " + username + " is unauthorized to use bot."));
                        }
                        return commandsFactory.getCommand(context.getCommand()).process(context);
                    })
                    .doOnNext(message -> {
                        var result = messageExecutor.execute(message);
                        if (result instanceof Message resultMessage && ContextState.progress.equals(context.getState())) {
                            context.addQuestionMessage(resultMessage);
                        }
                    });
            });
    }
}
```

  Note: `ErrorHandlerFactory` is **not** a dependency of the handler (the spec lists it among
  the handler's collaborators, but the current fold never references it — error routing lives
  in the coordinator's per-group `onErrorResume`). The handler depends only on
  `CommandsFactory`, `AuthInterceptor`, `MessageExecutor`. (If a future change pushes the
  per-group `onErrorResume` into the handler, add it then; keeping it out now is verbatim and
  behavior-preserving.)

- [ ] **Modify `CommandsSessionBot.java`** — inject `TelegramUpdateHandler`, delegate in
  `init()`, delete the bot's `handleUpdates`, and drop the now-unused fold-only fields/imports.
  Add field + constructor param:

  Field block after Task 2 has `commandsFactory`, `errorHandler`, `authInterceptor`,
  `properties`, `messageExecutor`, `outboundMessages`. Add `updateHandler`; the bot no longer
  uses `authInterceptor` directly (only the handler does) — but the **bot bean still receives
  it** to construct nothing now; per Task 4 the bot's constructor is slimmed. For this task,
  keep the constructor shape but **add `TelegramUpdateHandler updateHandler`** and stop using
  `authInterceptor` in `init`. To keep this task minimal and green, change the field and
  constructor to:

```java
    private final CommandsFactory commandsFactory;
    private final ErrorHandlerFactory errorHandler;
    private final MessageExecutor messageExecutor;
    private final OutboundMessages outboundMessages;
    private final TelegramUpdateHandler updateHandler;
    private final Sinks.Many<Update> updatesSink = Sinks.many().unicast().onBackpressureBuffer();
    private Disposable subscription;

    public CommandsSessionBot(
        CommandsFactory commandsFactory,
        ErrorHandlerFactory errorHandler,
        MessageExecutor messageExecutor,
        OutboundMessages outboundMessages,
        TelegramUpdateHandler updateHandler
    ) {
        this.commandsFactory = commandsFactory;
        this.errorHandler = errorHandler;
        this.messageExecutor = messageExecutor;
        this.outboundMessages = outboundMessages;
        this.updateHandler = updateHandler;
    }
```

  (This drops `authInterceptor` and `properties` from the bot constructor — the config `bot`
  bean is updated below; `properties` removal anticipates Task 4 and is safe now because the
  field was already dead.) In `init()`, replace `this.handleUpdates(...)` with the injected
  handler. Before:

```java
                    .flatMap(updates -> this.handleUpdates(updates.publishOn(Schedulers.boundedElastic()))
```

After:

```java
                    .flatMap(updates -> updateHandler.handleUpdates(updates.publishOn(Schedulers.boundedElastic()))
```

  **Delete** the bot's entire `handleUpdates(...)` method and the private `executeMessage`
  delegate (its only remaining callers — `setMyCommands.doOnNext`, the per-group
  `onErrorResume`, and the outbound `doOnNext` — switch to `messageExecutor::execute`).
  Update those three call sites in `init()`:

```java
        this.subscription = Flux.concat(
                setMyCommands.doOnNext(messageExecutor::execute),
                updatesSink.asFlux()
                    .map(UpdateWrapper::wrap)
                    .groupBy(UpdateWrapper::getChatId)
                    .flatMap(updates -> updateHandler.handleUpdates(updates.publishOn(Schedulers.boundedElastic()))
                        .onErrorResume(error -> {
                            log.warn("Handling pipeline error in a chat group", error);
                            return errorHandler.handle(error).doOnNext(messageExecutor::execute);
                        }))
                    .mergeWith(outboundMessages.messages().publishOn(Schedulers.boundedElastic()).doOnNext(messageExecutor::execute))
            ).subscribe(
                message -> { },
                error -> log.error("Bot pipeline terminated unexpectedly", error)
            );
```

  Remove from `CommandsSessionBot.java` the now-unused imports: `AuthInterceptor`,
  `CommandsSessionBotProperties`, `BotAuthException`, `CommandContext`, `ContextState`,
  `Message`, `Assert`, `Serializable`. Keep `CommandsFactory` (startup list),
  `ErrorHandlerFactory` (per-group `onErrorResume`), `UpdateWrapper`, `SetMyCommands`,
  `BotCommand`, `PartialBotApiMethod` (no longer needed — verify: after deleting
  `executeMessage`, `PartialBotApiMethod` is unused → remove), `Update`, `Sinks`, `Flux`,
  `Schedulers`, `Disposable`, `@PostConstruct`/`@PreDestroy`, `@Slf4j`.

- [ ] **Modify `CommandsSessionBotConfiguration.java`** — add the `telegramUpdateHandler` bean
  and repoint `bot`. Add `import com.kb.sessionbot.TelegramUpdateHandler;`:

```java
    @Bean
    @ConditionalOnMissingBean
    public TelegramUpdateHandler telegramUpdateHandler(
            CommandsFactory commandsFactory,
            AuthInterceptor authInterceptor,
            MessageExecutor messageExecutor) {
        return new TelegramUpdateHandler(commandsFactory, authInterceptor, messageExecutor);
    }

    @Bean
    public CommandsSessionBot bot(
            CommandsFactory commandsFactory,
            ErrorHandlerFactory errorHandler,
            MessageExecutor messageExecutor,
            OutboundMessages outboundMessages,
            TelegramUpdateHandler telegramUpdateHandler) {
        return new CommandsSessionBot(commandsFactory, errorHandler, messageExecutor, outboundMessages, telegramUpdateHandler);
    }
```

  (The spec's listed `ErrorHandlerFactory` dependency on the handler is intentionally omitted —
  see the note above.)

- [ ] **Modify `CommandsSessionBotTest.java`** — the bot is no longer the seam for the fold.
  **Delete** the following tests (they move to `TelegramUpdateHandlerTest`):
  `commandStartsFreshContext`, `completedCommandExecutesItsResponse`,
  `nonCommandAppendsToContext`, `emptyContextUsesHelp`, `authRejectWithMissingUserDoesNotNpe`,
  `refreshContextRebuild`, `authRejectSurfacesError`, `progressRecordsQuestionMessage`, and the
  `Concurrency#perChatOrderingIsPreserved` test (it asserts the fold ordering directly through
  `handleUpdates`). Keep `consumeEndToEnd` and `Concurrency#failureInOneChatDoesNotKillPipeline`
  (both go through `consume`/`init` — retargeted fully in Task 4). Update the bot factory to the
  new constructor and build a `TelegramUpdateHandler`:

```java
    private CommandsSessionBot bot(AuthInterceptor auth) {
        var executor = new TelegramClientMessageExecutor(telegramClient, errorHandlerFactory);
        var updateHandler = new TelegramUpdateHandler(commandsFactory, auth, executor);
        return new CommandsSessionBot(
            commandsFactory, errorHandlerFactory, executor, outboundMessages, updateHandler);
    }
```

  Remove the now-unused imports from `CommandsSessionBotTest` that only the deleted tests used
  (`BotAuthException`, `SendMessage` may still be used by remaining tests — verify before
  removing; `StepVerifier`, `Flux` are used by `TelegramUpdateHandlerTest`, not necessarily
  here). Leave imports that the remaining `consumeEndToEnd`/`failureInOneChat…` tests need.

- [ ] **Create `src/test/java/com/kb/sessionbot/TelegramUpdateHandlerTest.java`** — every fold
  case, driven through the public `handleUpdates` with the real `commandsFactory`/
  `errorHandlerFactory` fixtures and a real `TelegramClientMessageExecutor` over a mocked
  client (so `progress` records the question message and `completed` executes its response):

```java
package com.kb.sessionbot;

import com.kb.sessionbot.auth.AuthInterceptor;
import com.kb.sessionbot.commands.CommandsFactory;
import com.kb.sessionbot.commands.HelpCommand;
import com.kb.sessionbot.commands.IBotCommand;
import com.kb.sessionbot.commands.dispatcher.DispatcherBotCommand;
import com.kb.sessionbot.errors.exception.BotAuthException;
import com.kb.sessionbot.errors.handler.BotAuthErrorHandler;
import com.kb.sessionbot.errors.handler.BotCommandErrorHandler;
import com.kb.sessionbot.errors.handler.ErrorHandler;
import com.kb.sessionbot.errors.handler.ErrorHandlerFactory;
import com.kb.sessionbot.fixtures.EchoCommand;
import com.kb.sessionbot.fixtures.Fixtures;
import com.kb.sessionbot.fixtures.FixtureCommandConfig;
import com.kb.sessionbot.fixtures.OrderCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class TelegramUpdateHandlerTest {

    private static final AuthInterceptor ALLOW = ctx -> Mono.just(true);
    private static final AuthInterceptor DENY = ctx -> Mono.just(false);

    private AnnotationConfigApplicationContext springContext;
    private TelegramClient telegramClient;
    private CommandsFactory commandsFactory;
    private ErrorHandlerFactory errorHandlerFactory;

    @BeforeEach
    void setUp() {
        springContext = new AnnotationConfigApplicationContext(FixtureCommandConfig.class);

        List<IBotCommand> commands = List.of(
            new DispatcherBotCommand(springContext.getBean(OrderCommand.class), springContext),
            new DispatcherBotCommand(springContext.getBean(EchoCommand.class), springContext));
        var helpCommand = new HelpCommand(commands);
        commandsFactory = new CommandsFactory(helpCommand, commands);
        commandsFactory.start();

        errorHandlerFactory = new ErrorHandlerFactory(
            List.<ErrorHandler<?>>of(new BotCommandErrorHandler(), new BotAuthErrorHandler()));
        errorHandlerFactory.init();

        telegramClient = Mockito.mock(TelegramClient.class);
        Mockito.when(telegramClient.execute(any(BotApiMethod.class)))
            .thenReturn(Fixtures.message(Fixtures.CHAT_ID, 999, "sent"));
    }

    @AfterEach
    void tearDown() {
        springContext.close();
    }

    private TelegramUpdateHandler handler(AuthInterceptor auth) {
        return new TelegramUpdateHandler(
            commandsFactory, auth,
            new TelegramClientMessageExecutor(telegramClient, errorHandlerFactory));
    }

    @DisplayName("command update completes and emits its SendMessage response")
    @Test
    void commandStartsFreshContext() {
        var handler = handler(ALLOW);
        var updates = Flux.just(Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/order?buy&book")));

        StepVerifier.create(handler.handleUpdates(updates))
            .assertNext(m -> {
                assertThat(m).isInstanceOf(SendMessage.class);
                assertThat(((SendMessage) m).getText()).isEqualTo("buy:book");
            })
            .verifyComplete();
    }

    @DisplayName("completed command executes its response against the client")
    @Test
    void completedCommandExecutesItsResponse() {
        var handler = handler(ALLOW);
        var updates = Flux.just(Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/order?buy&book")));

        StepVerifier.create(handler.handleUpdates(updates)).expectNextCount(1).verifyComplete();

        var executed = ArgumentCaptor.forClass(BotApiMethod.class);
        verify(telegramClient, atLeastOnce()).execute(executed.capture());
        assertThat(executed.getAllValues())
            .anyMatch(m -> m instanceof SendMessage && "buy:book".equals(((SendMessage) m).getText()));
    }

    @DisplayName("non-command answer appends to the in-progress context and completes it")
    @Test
    void nonCommandAppendsToContext() {
        var handler = handler(ALLOW);
        var updates = Flux.just(
            Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/order?buy")),
            Fixtures.wrap(Fixtures.callbackUpdate(2, Fixtures.CHAT_ID, 101, "book")));

        StepVerifier.create(handler.handleUpdates(updates).filter(m -> m instanceof SendMessage)
                .map(m -> ((SendMessage) m).getText()))
            .expectNextMatches(text -> text.contains("product"))
            .expectNext("buy:book")
            .verifyComplete();
    }

    @DisplayName("per-chat updates process in arrival order under concatMap")
    @Test
    void perChatOrderingIsPreserved() {
        var handler = handler(ALLOW);
        var updates = Flux.just(
            Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/order?buy")),
            Fixtures.wrap(Fixtures.callbackUpdate(2, Fixtures.CHAT_ID, 101, "book")));

        StepVerifier.create(handler.handleUpdates(updates)
                .filter(m -> m instanceof SendMessage)
                .map(m -> ((SendMessage) m).getText()))
            .expectNextMatches(text -> text.contains("product"))
            .expectNext("buy:book")
            .verifyComplete();
    }

    @DisplayName("empty context routes to HelpCommand")
    @Test
    void emptyContextUsesHelp() {
        var handler = handler(ALLOW);
        var updates = Flux.just(Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "book")));

        StepVerifier.create(handler.handleUpdates(updates))
            .assertNext(m -> {
                assertThat(m).isInstanceOf(SendMessage.class);
                assertThat(((SendMessage) m).getText()).contains("Помощь");
            })
            .verifyComplete();
    }

    @DisplayName("auth rejection with a missing user surfaces BotAuthException, not NPE")
    @Test
    void authRejectWithMissingUserDoesNotNpe() {
        var handler = handler(DENY);
        var update = new org.telegram.telegrambots.meta.api.objects.Update();
        update.setUpdateId(1);
        update.setMessage(org.telegram.telegrambots.meta.api.objects.message.Message.builder()
            .messageId(100)
            .chat(org.telegram.telegrambots.meta.api.objects.chat.Chat.builder().id(Fixtures.CHAT_ID).type("private").build())
            .text("/order?buy&book")
            .build()); // no .from(...)
        var updates = Flux.just(Fixtures.wrap(update));

        StepVerifier.create(handler.handleUpdates(updates))
            .expectError(BotAuthException.class)
            .verify();
    }

    @DisplayName("refreshContext rebuilds the context from the original command")
    @Test
    void refreshContextRebuild() {
        var handler = handler(ALLOW);
        var updates = Flux.just(
            Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/order?buy")),
            Fixtures.wrap(Fixtures.callbackUpdate(2, Fixtures.CHAT_ID, 101, "book#refreshContext")));

        StepVerifier.create(handler.handleUpdates(updates)
                .filter(m -> m instanceof SendMessage)
                .map(m -> ((SendMessage) m).getText()))
            .expectNextMatches(text -> text.contains("product"))
            .expectNext("buy:book")
            .verifyComplete();
    }

    @DisplayName("auth rejection surfaces BotAuthException")
    @Test
    void authRejectSurfacesError() {
        var handler = handler(DENY);
        var updates = Flux.just(Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/order?buy&book")));

        StepVerifier.create(handler.handleUpdates(updates))
            .expectError(BotAuthException.class)
            .verify();
    }

    @DisplayName("progress state records the question message via addQuestionMessage side effect")
    @Test
    void progressRecordsQuestionMessage() {
        var handler = handler(ALLOW);
        var updates = Flux.just(Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/order?buy")));

        StepVerifier.create(handler.handleUpdates(updates))
            .assertNext(m -> {
                assertThat(m).isInstanceOf(SendMessage.class);
                assertThat(((SendMessage) m).getText()).contains("product");
            })
            .verifyComplete();

        verify(telegramClient, timeout(2000)).execute(any(BotApiMethod.class));
    }
}
```

- [ ] **Gate:** `export JAVA_HOME=$(/usr/libexec/java_home -v 21); mvn clean test-compile`
  then `mvn test -Dtest=TelegramUpdateHandlerTest,CommandsSessionBotTest,MessageExecutorTest,OutboundMessagesTest`.
  Green.
- [ ] **Commit:** `refactor: extract TelegramUpdateHandler from CommandsSessionBot`.

---

## Task 4 — Slim `CommandsSessionBot` to the coordinator (3 independent subscriptions)

**Outcome:** the coordinator no longer merges streams into one subscription. `init()` builds
**three independent subscriptions** in a `Disposables.composite()`, each disposed in
`@PreDestroy`. The `properties` field is fully gone (already dropped in Task 3's constructor;
this task removes any lingering reference and confirms the config bean shape). The
SetMyCommands mono is built from `commandsFactory.getCommands()`. The bot test is retargeted to
the coordinator: `consume` feeds the pipeline end-to-end, ordering/failure-isolation hold,
`@PreDestroy` disposes.

**Files:** modify `CommandsSessionBot.java`, `CommandsSessionBotConfiguration.java` (confirm
already correct from Task 3), `CommandsSessionBotTest.java`.

### Steps

- [ ] **Modify `CommandsSessionBot.java`** — replace the merged `Flux.concat(...).subscribe()`
  with three independent subscriptions in a composite. Switch `Disposable subscription` to
  `Disposable.Composite subscriptions = Disposables.composite()`. Before (`init` + `shutdown`):

```java
    @PostConstruct
    public void init() {
        var setMyCommands = Flux.fromIterable(commandsFactory.getCommands())
            .filter(command -> !command.hidden())
            .map(command -> BotCommand.builder().command(command.getCommandIdentifier()).description(command.getDescription()).build())
            .collectList()
            .map(commands -> SetMyCommands.builder().commands(commands).build());

        this.subscription = Flux.concat(
                setMyCommands.doOnNext(messageExecutor::execute),
                updatesSink.asFlux()
                    .map(UpdateWrapper::wrap)
                    .groupBy(UpdateWrapper::getChatId)
                    .flatMap(updates -> updateHandler.handleUpdates(updates.publishOn(Schedulers.boundedElastic()))
                        .onErrorResume(error -> {
                            log.warn("Handling pipeline error in a chat group", error);
                            return errorHandler.handle(error).doOnNext(messageExecutor::execute);
                        }))
                    .mergeWith(outboundMessages.messages().publishOn(Schedulers.boundedElastic()).doOnNext(messageExecutor::execute))
            ).subscribe(
                message -> { },
                error -> log.error("Bot pipeline terminated unexpectedly", error)
            );
    }

    @PreDestroy
    public void shutdown() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }
```

After:

```java
    @PostConstruct
    public void init() {
        var setMyCommands = Flux.fromIterable(commandsFactory.getCommands())
            .filter(command -> !command.hidden())
            .map(command -> BotCommand.builder().command(command.getCommandIdentifier()).description(command.getDescription()).build())
            .collectList()
            .map(commands -> SetMyCommands.builder().commands(commands).build());

        subscriptions.add(
            setMyCommands.subscribe(
                messageExecutor::execute,
                error -> log.error("Bot pipeline terminated unexpectedly", error)));

        subscriptions.add(
            updatesSink.asFlux()
                .map(UpdateWrapper::wrap)
                .groupBy(UpdateWrapper::getChatId)
                .flatMap(updates -> updateHandler.handleUpdates(updates.publishOn(Schedulers.boundedElastic()))
                    .onErrorResume(error -> {
                        log.warn("Handling pipeline error in a chat group", error);
                        return errorHandler.handle(error).doOnNext(messageExecutor::execute);
                    }))
                .subscribe(
                    ignored -> { },
                    error -> log.error("Bot pipeline terminated unexpectedly", error)));

        subscriptions.add(
            outboundMessages.messages()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(messageExecutor::execute)
                .subscribe(
                    ignored -> { },
                    error -> log.error("Bot pipeline terminated unexpectedly", error)));
    }

    @PreDestroy
    public void shutdown() {
        if (!subscriptions.isDisposed()) {
            subscriptions.dispose();
        }
    }
```

  Change the field:

```java
    private Disposable subscription;
```
  →
```java
    private final Disposable.Composite subscriptions = Disposables.composite();
```

  Update imports in `CommandsSessionBot.java`: keep `reactor.core.Disposable` (the
  `Disposable.Composite` nested type), add `import reactor.core.Disposables;`. Confirm
  `Schedulers`, `Flux`, `Sinks`, `SetMyCommands`, `BotCommand`, `UpdateWrapper`, `Update`,
  `CommandsFactory`, `ErrorHandlerFactory`, `MessageExecutor`, `OutboundMessages`,
  `TelegramUpdateHandler`, `@PostConstruct`/`@PreDestroy`, `@Slf4j` remain. The `properties`
  field and `CommandsSessionBotProperties` import were already removed in Task 3 — verify none
  linger.

  **Preserved guarantees (verify after edit):** inner `concatMap` lives in the handler
  (Task 3); outer `flatMap` over `groupBy(chatId)` with per-group
  `publishOn(Schedulers.boundedElastic())` and per-group `onErrorResume` stay in subscription 2
  verbatim; `updatesSink.emitNext(..., FAIL_FAST)` in `consume` unchanged;
  `outboundMessages.sendMessage`'s busyLooping unchanged (Task 2); outbound
  `publishOn(Schedulers.boundedElastic())` preserved in subscription 3. `SetMyCommands` no
  longer strictly precedes updates (independent subscriptions race) — accepted per spec.

- [ ] **Confirm `CommandsSessionBotConfiguration.java`** — the `bot` bean from Task 3 already
  has the final signature (`CommandsFactory, ErrorHandlerFactory, MessageExecutor,
  OutboundMessages, TelegramUpdateHandler`) and no `CommandsSessionBotProperties`. No change
  needed here; `telegramClient` and `telegramBotsApplication` beans are unchanged
  (`registerBot(properties.getToken(), bot)` still registers the coordinator).

- [ ] **Modify `CommandsSessionBotTest.java`** — retarget to the coordinator. The remaining
  tests are `consumeEndToEnd` (rename intent: feeds the pipeline via `consume`) and
  `failureInOneChatDoesNotKillPipeline`. Add a `disposeOnShutdown` test. Final test body:

```java
package com.kb.sessionbot;

import com.kb.sessionbot.auth.AuthInterceptor;
import com.kb.sessionbot.commands.CommandsFactory;
import com.kb.sessionbot.commands.HelpCommand;
import com.kb.sessionbot.commands.IBotCommand;
import com.kb.sessionbot.commands.dispatcher.DispatcherBotCommand;
import com.kb.sessionbot.errors.handler.BotAuthErrorHandler;
import com.kb.sessionbot.errors.handler.BotCommandErrorHandler;
import com.kb.sessionbot.errors.handler.ErrorHandler;
import com.kb.sessionbot.errors.handler.ErrorHandlerFactory;
import com.kb.sessionbot.fixtures.EchoCommand;
import com.kb.sessionbot.fixtures.Fixtures;
import com.kb.sessionbot.fixtures.FixtureCommandConfig;
import com.kb.sessionbot.fixtures.OrderCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class CommandsSessionBotTest {

    private static final AuthInterceptor ALLOW = ctx -> Mono.just(true);
    private static final AuthInterceptor DENY = ctx -> Mono.just(false);

    private AnnotationConfigApplicationContext springContext;
    private TelegramClient telegramClient;
    private CommandsFactory commandsFactory;
    private ErrorHandlerFactory errorHandlerFactory;
    private OutboundMessages outboundMessages;

    @BeforeEach
    void setUp() {
        springContext = new AnnotationConfigApplicationContext(FixtureCommandConfig.class);

        List<IBotCommand> commands = List.of(
            new DispatcherBotCommand(springContext.getBean(OrderCommand.class), springContext),
            new DispatcherBotCommand(springContext.getBean(EchoCommand.class), springContext));
        var helpCommand = new HelpCommand(commands);
        commandsFactory = new CommandsFactory(helpCommand, commands);
        commandsFactory.start();

        errorHandlerFactory = new ErrorHandlerFactory(
            List.<ErrorHandler<?>>of(new BotCommandErrorHandler(), new BotAuthErrorHandler()));
        errorHandlerFactory.init();

        telegramClient = Mockito.mock(TelegramClient.class);
        Mockito.when(telegramClient.execute(any(BotApiMethod.class)))
            .thenReturn(Fixtures.message(Fixtures.CHAT_ID, 999, "sent"));

        outboundMessages = new OutboundMessages();
    }

    @AfterEach
    void tearDown() {
        springContext.close();
    }

    private CommandsSessionBot bot(AuthInterceptor auth) {
        var executor = new TelegramClientMessageExecutor(telegramClient, errorHandlerFactory);
        var updateHandler = new TelegramUpdateHandler(commandsFactory, auth, executor);
        return new CommandsSessionBot(
            commandsFactory, errorHandlerFactory, executor, outboundMessages, updateHandler);
    }

    @DisplayName("consume() drives an update end-to-end to telegramClient.execute")
    @Test
    void consumeEndToEnd() {
        var bot = bot(ALLOW);
        bot.init();
        bot.consume(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/order?buy"));

        // SetMyCommands at startup + the executed prompt from the in-progress command.
        verify(telegramClient, timeout(2000).atLeast(2)).execute(any(BotApiMethod.class));
    }

    @DisplayName("a chat whose handler errors does not terminate the updates subscription (failure isolation)")
    @Test
    void failureInOneChatDoesNotKillPipeline() {
        var bot = bot(DENY); // auth denial makes the first chat's dispatch error
        bot.init();

        bot.consume(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/order?buy&book"));
        long otherChat = Fixtures.CHAT_ID + 1;
        bot.consume(Fixtures.messageUpdate(2, otherChat, 200, "anything"));

        // SetMyCommands + the auth error message + chat B's help response all execute.
        verify(telegramClient, timeout(5000).atLeast(3)).execute(any(BotApiMethod.class));
    }

    @DisplayName("out-of-band messages pushed via OutboundMessages execute through the coordinator")
    @Test
    void outboundMessagesExecuteThroughCoordinator() {
        var bot = bot(ALLOW);
        bot.init();

        outboundMessages.sendMessage(SendMessage.builder()
            .chatId(String.valueOf(Fixtures.CHAT_ID)).text("out-of-band").build());

        // SetMyCommands + the out-of-band SendMessage.
        verify(telegramClient, timeout(5000).atLeast(2)).execute(any(BotApiMethod.class));
    }

    @DisplayName("@PreDestroy disposes the subscription composite")
    @Test
    void shutdownDisposesSubscriptions() {
        var bot = bot(ALLOW);
        bot.init();
        bot.shutdown();

        // After disposal, a freshly consumed update no longer drives the (disposed) updates
        // subscription; nothing new is executed beyond the startup SetMyCommands.
        bot.consume(Fixtures.messageUpdate(3, Fixtures.CHAT_ID, 300, "/order?buy"));
        verify(telegramClient, timeout(1000).atMost(1)).execute(any(BotApiMethod.class));
    }
}
```

  Note on `shutdownDisposesSubscriptions`: after `shutdown()` the updates subscription is
  disposed; `updatesSink.emitNext(update, FAIL_FAST)` in `consume` then has no subscriber. With
  a unicast `onBackpressureBuffer` sink that has already terminated its subscription, FAIL_FAST
  surfaces a non-serialized emission as a no-op/failure result that `consume` ignores (it does
  not re-throw). The assertion `atMost(1)` (only the startup `SetMyCommands` executed) is the
  observable proof of disposal. If FAIL_FAST emission behavior on a disposed unicast sink turns
  out to throw in this telegrambots/Reactor combo, fall back to asserting disposal directly by
  exposing a package-private `boolean isDisposed()` on the bot returning
  `subscriptions.isDisposed()` and asserting that instead — decide during implementation based
  on observed `StepVerifier`/Mockito behavior. **Confidence: medium** on the no-op variant;
  **high** on the `isDisposed()` fallback.

- [ ] **Gate:** `export JAVA_HOME=$(/usr/libexec/java_home -v 21); mvn clean test-compile`
  then `mvn test -Dtest=CommandsSessionBotTest,TelegramUpdateHandlerTest,MessageExecutorTest,OutboundMessagesTest`.
  Green.
- [ ] **Commit:** `refactor: reduce CommandsSessionBot to a thin coordinator`.

---

## Self-review against the spec

- **MessageExecutor** (interface + `TelegramClientMessageExecutor` impl): Task 1. Media
  `switch` verbatim; `execute` is the method name used consistently in all four tasks.
- **OutboundMessages** (`messagesSink` + `sendMessage` + `messages()`): Task 2. Names
  `sendMessage`/`messages` used consistently.
- **TelegramUpdateHandler** (public `handleUpdates`): Task 3. Fold verbatim. (Deviation from
  spec's collaborator list: `ErrorHandlerFactory` is **not** injected into the handler because
  the current fold never references it — error routing stays in the coordinator's per-group
  `onErrorResume`. Documented in Task 3 and in Assumptions below.)
- **Coordinator** (`LongPollingSingleThreadUpdateConsumer`, `updatesSink` + `consume`, three
  independent subscriptions in `Disposables.composite()`, `@PreDestroy`, no properties/
  sendMessage/executeMessage/handleUpdates): Task 4.
- **Subscription model:** three independent subscriptions, each `.subscribe(..., errorLogger)`;
  per-group `publishOn(boundedElastic)` + `onErrorResume`; outbound `publishOn(boundedElastic)`
  + `doOnNext(execute)`; startup `SetMyCommands` → `execute`. All in Task 4, verbatim operators.
- **Auto-config:** all new beans `@ConditionalOnMissingBean` (Tasks 1–3);
  `telegramClient`/`telegramBotsApplication` unchanged.
- **Test retargeting:** `MessageExecutorTest` (Task 1), `OutboundMessagesTest` (Task 2),
  `TelegramUpdateHandlerTest` (Task 3), retargeted `CommandsSessionBotTest` (Task 4).
  Concurrency tests preserved: ordering → `TelegramUpdateHandlerTest#perChatOrderingIsPreserved`;
  concurrent-sendMessage-loses-nothing → `OutboundMessagesTest#concurrentSendMessageLosesNothing`;
  failure-isolation → `CommandsSessionBotTest#failureInOneChatDoesNotKillPipeline`.
- **Names consistent across tasks:** `MessageExecutor.execute`, `OutboundMessages.sendMessage`/
  `messages`, `TelegramUpdateHandler.handleUpdates`. Confirmed.
- **No placeholders:** every step shows complete before/after production code and complete test
  code.