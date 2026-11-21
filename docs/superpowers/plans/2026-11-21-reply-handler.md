# ReplyHandler Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A bot bean can claim a bare text message reactively, with the update context, and say whether it took it.

**Architecture:** Two public types in `handler` (`ReplyHandler`, `Reply`). `TelegramUpdateHandler` asks the `ReplyHandler` beans, in order, before the `TextHandler` beans; the text branch of bare dispatch becomes reactive only when at least one `ReplyHandler` exists, so a bot without one runs today's code path unchanged. The library stores no state.

**Tech Stack:** Java 25, Spring Boot 4.1 auto-configuration, Project Reactor, telegrambots-meta 10, JUnit 5, AssertJ, Mockito, reactor-test.

**Spec:** `docs/superpowers/specs/2026-11-21-reply-handler-design.md`

## Global Constraints

- Branch `feature/reply-handler`; never commit to `master`. No AI attribution trailers. Never stage `target/`, `.idea/`, `.DS_Store`, `.claude/`.
- Version stays `0.2.0-SNAPSHOT`. Additive only: every existing public constructor and interface keeps its signature.
- Build and test on JDK 25: `export JAVA_HOME=$(/usr/libexec/java_home -v 25)`.
- Only `io.github.baevkir.sessionbot` and its `handler`/`autoconfigure`/… subpackages are API; `internal` is not.
- No one-letter identifiers (lambda parameters included). Public members first, private helpers after, nested types last (production classes; tests keep fixtures on top).
- Javadoc: one or two sentences per type, saying what it is.
- Dispatch order for a bare update: contact, document, photo, `ReplyHandler` beans, `TextHandler` beans, `/help`.
- With no `ReplyHandler` bean, behaviour is byte-for-byte today's, including the `/help` fallback that never runs the `AuthInterceptor`.

## Review Focus

1. `claim` throws synchronously instead of returning a failed `Mono`: the chat gets the ordinary `BotCommandException`, the stream does not die silently. Test in Task 2.
2. `claim` returns `null`: same outcome as a thrown error, not an NPE that escapes unmapped. Test in Task 2.
3. `Reply.of` is given a `null` message or a list the caller later mutates: rejected at creation, and `messages()` is an unmodifiable copy. Test in Task 1.
4. Two texts arrive back to back while the first `claim` is still running: they are claimed one after the other in arrival order (the per-chat `concatMap` already guarantees it). Test in Task 2.
5. A `ReplyHandler` claims a text that looks like nothing special, and a `TextHandler` that `supports` everything is also registered: the `TextHandler` never sees it. Test in Task 2.

## File Structure

| File | Change | Responsibility |
|---|---|---|
| `src/main/java/io/github/baevkir/sessionbot/handler/Reply.java` | create | The result of a claimed text: messages to send, possibly none |
| `src/main/java/io/github/baevkir/sessionbot/handler/ReplyHandler.java` | create | The seam a bot implements |
| `src/main/java/io/github/baevkir/sessionbot/internal/TelegramUpdateHandler.java` | modify | Asks reply handlers before text handlers |
| `src/main/java/io/github/baevkir/sessionbot/autoconfigure/SessionBotAutoConfiguration.java` | modify (`telegramUpdateHandler` bean, ~l.121) | Collects `ReplyHandler` beans |
| `src/test/java/io/github/baevkir/sessionbot/handler/ReplyTest.java` | create | |
| `src/test/java/io/github/baevkir/sessionbot/internal/TelegramUpdateHandlerTest.java` | modify | Dispatch tests |
| `src/test/java/io/github/baevkir/sessionbot/autoconfigure/SessionBotAutoConfigurationTest.java` | modify | Wiring test |
| `README.md`, `CHANGELOG.md` | modify | |

---

### Task 1: `Reply` and `ReplyHandler`

**Files:**
- Create: `src/main/java/io/github/baevkir/sessionbot/handler/Reply.java`
- Create: `src/main/java/io/github/baevkir/sessionbot/handler/ReplyHandler.java`
- Test: `src/test/java/io/github/baevkir/sessionbot/handler/ReplyTest.java`

**Interfaces:**
- Produces:
  ```java
  public final class Reply {
      public static Reply of(PartialBotApiMethod<?>... messages);
      public static Reply of(List<? extends PartialBotApiMethod<?>> messages);
      public static Reply silent();
      public List<PartialBotApiMethod<?>> messages();
  }

  public interface ReplyHandler {
      Mono<Reply> claim(CommandContext context, String text);
  }
  ```

- [ ] **Step 1: Write the failing tests** in `ReplyTest`:

```java
@Test void silentCarriesNoMessages() {
    assertThat(Reply.silent().messages()).isEmpty();
}

@Test void ofKeepsTheMessagesInOrder() {
    var first = SendMessage.builder().chatId("1").text("a").build();
    var second = SendMessage.builder().chatId("1").text("b").build();
    assertThat(Reply.of(first, second).messages()).containsExactly(first, second);
}

@Test void aLaterChangeToTheCallersListDoesNotReachTheReply() {
    var messages = new ArrayList<PartialBotApiMethod<?>>();
    messages.add(SendMessage.builder().chatId("1").text("a").build());
    var reply = Reply.of(messages);
    messages.clear();
    assertThat(reply.messages()).hasSize(1);
    assertThatThrownBy(() -> reply.messages().clear()).isInstanceOf(UnsupportedOperationException.class);
}

@Test void aNullMessageIsRefusedAtCreation() {
    assertThatNullPointerException().isThrownBy(() -> Reply.of((PartialBotApiMethod<?>) null));
    assertThatNullPointerException().isThrownBy(() -> Reply.of(Arrays.asList((PartialBotApiMethod<?>) null)));
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `mvn -q test -Dtest=ReplyTest`
Expected: compilation failure, `Reply` not found.

- [ ] **Step 3: Implement both types.** `Reply` is a final class with a private constructor holding `List.copyOf(messages)` (which also rejects nulls); `silent()` returns one shared empty instance. `ReplyHandler` javadoc: "Claims a plain text message the bot was waiting for, before any `TextHandler` sees it. An empty result means the text is not this handler's and the next one is asked."

- [ ] **Step 4: Run to verify they pass**

Run: `mvn -q test -Dtest=ReplyTest`
Expected: 4 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/baevkir/sessionbot/handler/Reply.java src/main/java/io/github/baevkir/sessionbot/handler/ReplyHandler.java src/test/java/io/github/baevkir/sessionbot/handler/ReplyTest.java
git commit -m "feat: Reply and ReplyHandler, a text a bot was waiting for"
```

---

### Task 2: The dispatcher asks reply handlers before text handlers

**Files:**
- Modify: `src/main/java/io/github/baevkir/sessionbot/internal/TelegramUpdateHandler.java`
- Test: `src/test/java/io/github/baevkir/sessionbot/internal/TelegramUpdateHandlerTest.java`

**Interfaces:**
- Consumes: `ReplyHandler.claim`, `Reply.messages()` from Task 1.
- Produces: one new public constructor, the existing ten parameters followed by `List<ReplyHandler> replyHandlers`:
  ```java
  public TelegramUpdateHandler(CommandsFactory commandsFactory, AuthInterceptor authInterceptor,
      MessageExecutor messageExecutor, List<DocumentHandler> documentHandlers,
      List<ContactHandler> contactHandlers, List<TextHandler> textHandlers,
      List<PhotoHandler> photoHandlers, List<String> permitCommands,
      GuardDeniedHandler guardDeniedHandler, String botUsername, List<ReplyHandler> replyHandlers)
  ```
  The three existing constructors stay and delegate with `List.of()`; a `null` list is treated as empty.

**Test fixtures** (add beside `echoText` in `TelegramUpdateHandlerTest`):

```java
private TelegramUpdateHandler handlerWithReplies(AuthInterceptor auth, List<TextHandler> textHandlers,
                                                 List<ReplyHandler> replyHandlers) {
    return new TelegramUpdateHandler(
        commandsFactory, auth,
        new TelegramClientMessageExecutor(telegramClient, errorHandlerFactory),
        List.of(), List.of(), textHandlers, List.of(), List.of(),
        new HelpGuardDeniedHandler(commandsFactory.getHelpCommand()), null, replyHandlers);
}

private static SendMessage say(CommandContext context, String text) {
    return SendMessage.builder().chatId(context.getChatId()).text(text).build();
}
```

All tests send bare text with `Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "<text>")` and read results as `((SendMessage) sent).getText()`, as the existing text tests do.

- [ ] **Step 1: Write the failing tests.** One `@Test` each, with a `@DisplayName` in the file's style:

| Test method | Setup | Asserts |
|---|---|---|
| `aClaimedTextSendsTheReplyAndSkipsTextHandlers` | reply handler → `Reply.of(say(ctx, "claimed: " + text))`; `echoText(received)` registered | emits exactly `claimed: борщ & хліб #1` (text verbatim); `received` is null; `telegramClient.execute` called once |
| `aSilentReplySendsNothingAndSkipsTextHandlersAndHelp` | reply handler → `Reply.silent()`; `echoText(received)` | `verifyComplete()` with no element; `received` null; `telegramClient` never called |
| `anUnclaimedTextGoesToTheTextHandler` | reply handler → `Mono.empty()`; `echoText(received)` | emits `got: молоко`; `received` = `молоко` |
| `anUnclaimedTextWithNoTextHandlerFallsBackToHelp` | reply handler → `Mono.empty()`; no text handlers | one `SendMessage` whose text contains `Help`; `telegramClient.execute` called exactly once (help is not executed twice) |
| `replyHandlersAreAskedInOrderAndNoneAfterTheWinner` | three handlers recording their name into a `List<String>`: first empty, second claims with `second`, third claims | emits `second`; the recorded list is exactly `["first", "second"]` |
| `aCaptionedPhotoNeverReachesAReplyHandler` | spy reply handler; `Fixtures.photoUpdate(1, CHAT_ID, 100, "борщ", null)` | spy not invoked |
| `textTypedDuringACommandNeverReachesAReplyHandler` | spy reply handler; updates `/order` then `book` | spy not invoked; output as in the existing `text typed while a command waits…` test |
| `aRefusedSenderGetsTheAuthErrorAndNoClaim` | `DENY`; spy reply handler; `echoText(received)` | `verifyError(BotAuthException.class)`; spy not invoked; `received` null |
| `theInterceptorRunsOnceWhenTheTextFallsThrough` | interceptor counting calls into an `AtomicInteger` and allowing; reply handler empty; `echoText` | emits `got: …`; counter is 1 |
| `withoutReplyHandlersARefusedSendersUnsupportedTextStillGetsHelp` | `DENY`; no reply handlers; one text handler whose `supports` is false | one `SendMessage` containing `Help`, no error |
| `aFailedClaimSurfacesAsACommandErrorAndDoesNotFallThrough` | reply handler → `Mono.error(new IllegalStateException("boom"))`; `echoText(received)` | `verifyError(BotCommandException.class)`; `received` null |
| `aClaimThatThrowsSurfacesAsACommandError` | reply handler lambda throws `IllegalStateException` | `verifyError(BotCommandException.class)` |
| `aClaimThatReturnsNullSurfacesAsACommandError` | reply handler lambda returns `null` | `verifyError(BotCommandException.class)` |
| `twoTextsAreClaimedInArrivalOrder` | reply handler → `Mono.delay(Duration.ofMillis(50)).thenReturn(Reply.of(say(ctx, text)))`; two updates `перший`, `другий` | emits `перший` then `другий` |

- [ ] **Step 2: Run to verify they fail**

Run: `mvn -q test -Dtest=TelegramUpdateHandlerTest`
Expected: compilation failure, no 11-argument constructor.

- [ ] **Step 3: Implement.** Add the field and constructor. In `dispatchOutsideCommand`, keep contact, document and photo as they are; for the text branch, when `replyHandlers` is empty return exactly today's expression, otherwise return `Optional.of(dispatchReplies(context, text))`. The new private method:

```java
private Flux<PartialBotApiMethod<?>> dispatchReplies(ConversationState context, String text) {
    Mono<Flux<PartialBotApiMethod<?>>> claimed = Flux.fromIterable(replyHandlers)
        .concatMap(handler -> Mono.defer(() -> handler.claim(context, text)))
        .next()
        .map(reply -> Flux.fromIterable(reply.messages()));
    Mono<Flux<PartialBotApiMethod<?>>> unclaimed = Mono.fromSupplier(() -> textHandlers.stream()
        .filter(handler -> handler.supports(text))
        .findFirst()
        .map(handler -> Flux.<PartialBotApiMethod<?>>from(handler.handle(context, text)))
        .orElseGet(() -> Flux.from(commandsFactory.getHelpCommand().render(context))));
    return dispatchBare(context, "text", bareContext -> claimed.switchIfEmpty(unclaimed).flatMapMany(flux -> flux));
}
```

`Mono.defer` is what turns a thrown exception and a `null` return into an error signal (Reactor raises an NPE for the null, and `dispatchBare`'s `onErrorMap` wraps both). `dispatchBare` already runs the interceptor once, maps errors and executes every emitted message, so the help fallback must not add its own `doOnNext(messageExecutor::execute)` here.

- [ ] **Step 4: Run the whole dispatcher suite**

Run: `mvn -q test -Dtest='TelegramUpdateHandler*Test'`
Expected: all pass, the 14 new ones and every existing one.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/baevkir/sessionbot/internal/TelegramUpdateHandler.java src/test/java/io/github/baevkir/sessionbot/internal/TelegramUpdateHandlerTest.java
git commit -m "feat: reply handlers are asked for a bare text before text handlers"
```

---

### Task 3: Wiring and documentation

**Files:**
- Modify: `src/main/java/io/github/baevkir/sessionbot/autoconfigure/SessionBotAutoConfiguration.java` (`telegramUpdateHandler` bean)
- Test: `src/test/java/io/github/baevkir/sessionbot/autoconfigure/SessionBotAutoConfigurationTest.java`
- Modify: `README.md` ("Bare updates" list and order sentence, ~l.160-172; the `/help` fallback paragraph, ~l.249-252)
- Modify: `CHANGELOG.md` (0.2.0 "Added")

**Interfaces:**
- Consumes: the 11-argument constructor from Task 2.

- [ ] **Step 1: Write the failing test** `aReplyHandlerBeanIsTheOneTheDispatcherAsks`: with `activeRunner` plus a user configuration declaring one `ReplyHandler` bean that records the text into an `AtomicReference<String>` and returns `Reply.silent()`, and an `AuthInterceptor` bean that allows; take the `TelegramUpdateHandler` bean, feed it `Flux.just(Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "відповідь")))`, `verifyComplete()`, and assert the reference holds `відповідь`. If `activeRunner` already supplies an `AuthInterceptor`, reuse it rather than declaring a second.

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -Dtest=SessionBotAutoConfigurationTest`
Expected: the new test fails (the text reaches `/help`, the reference stays null).

- [ ] **Step 3: Wire it.** Add `ObjectProvider<ReplyHandler> replyHandlers` to the bean method and pass `replyHandlers.orderedStream().toList()` as the last constructor argument.

- [ ] **Step 4: Write the docs.**
  - README "Bare updates": add a `ReplyHandler` bullet (claims a text the bot was waiting for; `claim(CommandContext, String)` returns `Mono<Reply>`; empty passes the text on; `Reply.of(…)` or `Reply.silent()`; state lives in the bot). Change the order sentence to: "Dispatch tries contact, then document, then photo, then the `ReplyHandler` beans in order, then text, and the first match handles the update."
  - README `/help` fallback paragraph: add that a bot with a `ReplyHandler` bean runs the `AuthInterceptor` on every bare text before the first `claim`, so there an unauthorized sender's text ends in the auth refusal and not in `/help`.
  - CHANGELOG 0.2.0 "Added": a `ReplyHandler` entry naming both types, the order, the new `TelegramUpdateHandler` constructor and the authorization consequence.

- [ ] **Step 5: Run the full suite and install the snapshot**

Run: `mvn -q clean install`
Expected: BUILD SUCCESS, no test failures; `0.2.0-SNAPSHOT` in `~/.m2`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/baevkir/sessionbot/autoconfigure/SessionBotAutoConfiguration.java src/test/java/io/github/baevkir/sessionbot/autoconfigure/SessionBotAutoConfigurationTest.java README.md CHANGELOG.md
git commit -m "feat: ReplyHandler beans are wired into the dispatcher; docs"
```

---

## After the last task

- Commit the spec and this plan (`docs: design and plan for ReplyHandler`); in this repo they are tracked.
- Push the branch and open a PR against `master`. family-iot's CI builds the library from `master`, so the first bot change that declares a `ReplyHandler` needs this PR merged first.
- Nothing in family-iot changes in this plan. Verified 2026-11-21: no family-iot source or test constructs `TelegramUpdateHandler` directly.
