# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A **Spring Boot auto-configuration starter** (`io.github.baevkir:sessionbot-telegram-spring-boot-starter`) for building Telegram bots whose commands behave like multi-step conversations. A command can ask the user for missing arguments one at a time (via inline keyboards or text replies); the framework accumulates those answers into a per-chat session until the command has everything it needs to run. Published on JitPack (Maven Central later).

**Intended as a public, general-purpose library**, not tied to any single consuming app. Design every API for arbitrary consumers: no assumptions about roles, users, storage or a particular bot; extension points are interfaces/annotations the consuming app implements; new features are opt-in with backward-compatible defaults, so an existing bot upgrades without code changes. Domain rules (who is an admin, which chats exist) belong in the consuming app, never here.

Java 25, Spring Boot 4.1, Maven, Project Reactor, Lombok, `org.telegram:telegrambots-{meta,client,longpolling}` 10.0.0.

## Build & test

```bash
mvn clean install                                   # build + install to local repo
mvn package                                          # build jar
mvn test                                             # all tests
mvn test -Dtest=MessageDescriptorTest               # single test class
mvn test -Dtest=MessageDescriptorTest#parseCommandOnlyCommand   # single method
```

Note: `.gitignore` references Gradle, but this project builds with Maven (`pom.xml`). There are no application classes here — it's a library; downstream apps supply the `@BotCommand` beans and config.

## How it wires up (auto-configuration)

`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` → `io.github.baevkir.sessionbot.autoconfigure.SessionBotAutoConfiguration` (an `@AutoConfiguration` class, registered via the Spring Boot 3 imports mechanism), which is `@ConditionalOnProperty(prefix = "sessionbot.telegram", value = {"token", "bot-username"})`. A consuming app activates the bot purely by setting:

```
sessionbot.telegram.token=...
sessionbot.telegram.bot-username=...
```

The config scans the app context for beans annotated `@BotCommand` and wraps each in a `DispatcherBotCommand`. Most beans (error handlers, parameter renderers, `AuthInterceptor`) are `@ConditionalOnMissingBean`, so downstream apps override by simply declaring their own; `HelpCommand` and `CommandsFactory` are internal and no longer overridable beans.

## Core processing flow

`CommandsSessionBot` (a `LongPollingSingleThreadUpdateConsumer`, registered via `TelegramBotsLongPollingApplication` and sending through an OkHttp `TelegramClient`) is fully reactive — it does **not** process updates inline:

1. `consume` emits every `Update` onto an `InboundUpdateBus` (default `SinkInboundUpdateBus`), which wraps it, groups by chat id (`groupBy`) and applies the idle-TTL timeout, yielding one `ChatUpdateStream` per chat.
2. `CommandsSessionBot` fans out over those streams with `flatMap(_, maxConcurrentChats)`: at most `sessionbot.telegram.max-concurrent-chats` chats run at once, but one chat's own stream is still handled strictly in order.
3. `TelegramUpdateHandler.handleUpdates` drops updates addressed to another bot (group-chat `@addressee`), then `scanWith(ConversationState::empty, ...)` folds the stream into an evolving **`ConversationState`** (the internal, mutable implementation of `CommandContext`):
   - a command update (`/foo`) starts a fresh state,
   - a non-command update outside any command starts its own fresh (bare-update) state — so a burst of bare updates is never dropped waiting behind one context,
   - a non-command update inside a command's conversation appends to the current state,
   - the `refreshContext` dynamic param rebuilds the state from the original command.
4. The matched `RegisteredCommand.process(conversation)` returns a `Publisher<? extends PartialBotApiMethod<?>>`; each result is executed against the Telegram API. The chat's stream completes as soon as a step closes the state: a requested `/help`, an unknown command, and a guard denial all close it; the `/help` sent as the fallback for an unhandled bare update does **not**, since closing would cancel the chat's group and drop updates already queued behind it.

`ConversationState` holds the originating update, accumulated `answers`, sent `questionMessages`, and a `ContextState` (`open → progress → close`); commands, guards, handlers and renderers see it only through the read-only `CommandContext` interface. When a command needs more input it enters `progress`; when complete it `close`s and the framework deletes the prior question/answer messages (`DeleteMessage`) to keep the chat clean.

## Command dispatch & argument matching

A command class:

```java
@BotCommand(value = "order", description = "...", hidden = false)
public class OrderCommand {
    @CommandMethod(arguments = "buy&{product}")
    public Mono<SendMessage> buy(@Parameter("product") String product) { ... }
}
```

- `CommandsDispatcher` reflects over `@CommandMethod` methods. `MethodMatcher` scores each method's `arguments` template against the context's accumulated answers; literal segments must match, `{placeholder}` segments bind by name. Highest score wins (see `MethodMatcher.getMatchingScore`).
- For each `@Parameter` the dispatcher either pulls the bound answer (JSON-converted via Jackson to the parameter type) or, if missing and required, calls a **ParameterRenderer** to prompt the user and suspends the command in `progress` state.
- Method params can also be auto-injected by type+name without `@Parameter`: `UpdateWrapper command`/`update`, `Update update`, `User from`, `String chatId`, `DynamicParameters`, `CommandContext`.
- Method return values are normalized by `InvocationResultResolver` into a `Publisher<? extends PartialBotApiMethod<?>>` (a raw `PartialBotApiMethod`, a `Collection` of them, or a `Publisher` are all accepted).
- `@BotCommand` beans are adapted to the internal `RegisteredCommand` interface by `DispatcherBotCommand`, whose `process(ConversationState conversation)` returns `Publisher<? extends PartialBotApiMethod<?>>`.

## Wire format (callback data) — `MessageDescriptor` / `CommandBuilder`

Commands and inline-button callbacks are encoded as strings, parsed by `MessageDescriptor` and built by `CommandBuilder` (constants in `CommandConstants`):

```
/command@addressee?answer1&answer2#dynParam1:value&dynParam2
```

- `/` command prefix, `@addressee` an optional group-chat target (`UpdateWrapper.getAddressee()`), `?` separates command from answers, `&` separates answers, `#` introduces dynamic params, `:` is key/value.
- A message **without** a leading `/` is treated as answers/dynamic-params for the in-progress context (i.e. a button press or text reply). Typed text goes through `MessageDescriptor.parseTyped` instead of `parse`: a typed command keeps its answers but never carries dynamic params, and any other typed text is one verbatim answer.
- **64-byte limit**: Telegram caps callback data at 64 bytes; `CommandBuilder.build()` warns when exceeded. Keep command/answer strings short.
- `CommandBuilder`/`WireFormat` percent-escape the format's reserved characters (`% ? & #` in answers, plus `:` in dynamic params) so any answer or param value round-trips intact.

**Dynamic params** (control flags, set via `CommandBuilder`, read via `DynamicParameters`) — the only built-in ones: `refreshContext` (rebuild the context from the original command) and `skip:<index>` (`DynamicParameters.canSkipAnswer`; allow skipping optional answers up to index). Callback data never carries an addressee.

## Parameter renderers (prompting for input)

`ParameterRenderer.render(ParameterRequest) → Publisher<? extends PartialBotApiMethod<?>>` produces the message that asks the user for a value. There is no factory/hierarchy: `CommandsDispatcher` resolves the renderer straight from the `ApplicationContext` — by `@Rendering(type = ...)` if given, else by `@Rendering(name = ...)` (default `"defaultParameterRenderer"`).

Built-ins: `TextParameterRenderer`, `DateParameterRenderer`, `TimeParameterRenderer`, `BooleanParameterRenderer`, and `CompositeParameterRenderer` (the `defaultParameterRenderer` bean, which dispatches on the parameter's Java type). Select a renderer on a parameter via `@Parameter(rendering = @Rendering(name = "...", type = ..., options = {...}))`. A custom renderer is just another `ParameterRenderer` bean, referenced by name or type the same way.

## Auth & errors

- `AuthInterceptor.intercept(context) → Mono<Boolean>` gates every command (default bean allows all). Return `false` → `BotAuthException`.
- `ErrorHandlerFactory` dispatches thrown errors to `ErrorHandler` beans by exception type; `BotCommandErrorHandler` and `BotAuthErrorHandler` are the defaults. Domain exceptions: `BotCommandException`, `BotAuthException` (both carry the `CommandContext`).

## Command guards

- `@Guarded(MyGuard.class)` names one or more `CommandGuard` types on a `@BotCommand` class, directly or as a meta-annotation (e.g. an `@AdminOnly` that itself carries `@Guarded`). `GuardResolver.guardTypes` collects every occurrence — direct, inherited, and through meta-annotations — via `MergedAnnotations` (`SearchStrategy.TYPE_HIERARCHY`), combines them with AND, and de-duplicates repeats.
- Guards are Spring beans, not looked up lazily: `DispatcherBotCommand`'s constructor resolves each `@Guarded` type from the `ApplicationContext` immediately, so a command guarded by a type with no matching bean fails application **startup**, not a later request.
- `CommandGuards.permits(command, context)` is the single evaluation point — `TelegramUpdateHandler` (dispatch), `HelpCommand` (`/help`) and `CommandMenuService` (the per-chat menu) all call it, so the three can never disagree about who may see or run a command. It runs a command's guards in order and stops at the first denial; an error or an empty result from a guard is treated as a denial.
- Per update, the order is: permit-list check → `AuthInterceptor` → `CommandGuards.permits` → `command.process()`. `permitCommands` only skips the `AuthInterceptor`; guards still run and can still deny. Guards are re-checked on every update of an in-progress command's conversation, not just the first.
- `GuardDeniedHandler` answers a refused caller; the default (`HelpGuardDeniedHandler`) replies exactly as for an unknown command, so a guarded command's existence is never revealed. Override the bean for an explicit "no access" reply.
- The default command menu (`CommandMenus.defaultCommands`) excludes every guarded command, since a guard is per caller and that menu is shared by every chat.
- `CommandMenuService` is never called by the library itself — only by the application, to set or clear a chat-scoped menu (e.g. at startup or after a role change).