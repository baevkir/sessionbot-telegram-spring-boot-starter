# Public API Shape for 0.1.0 — Design Spec

> Shape the library's public surface before its first release (`0.1.0` on JitPack, later Maven
> Central): move to `io.github.baevkir` coordinates and packages, turn `CommandContext` into a
> read-only interface, key i18n on `User`, hide internals behind an `internal` package, and settle
> names, defaults and group-chat commands. Breaking by design; migration notes go to `CHANGELOG.md`.

## Goal

After `0.1.0` every public type is a compatibility promise. This change makes the public surface
exactly what a bot needs and nothing else, so later releases can evolve the internals freely.

Success criteria:

1. A command, guard, handler or renderer can read the conversation but cannot change its state.
2. Everything a bot is not meant to touch lives in `io.github.baevkir.sessionbot.internal`.
3. The owner's bots in `family-iot` migrate with import changes plus the short list in
   [Migration](#migration); no feature they use disappears.
4. All existing behavior covered by the test suite is preserved, except the deliberate changes
   listed below.

## Context

Decided with the owner during the pre-release review (subproject B; subproject A — security and
correctness fixes — is already on `fix/pre-release-hardening`, which this branch builds on). The
rename to `io.github.baevkir` (originally subproject D) is folded in here so classes move once.

The only known consumers are the owner's four modules in `family-iot` (`bot-admin-starter`,
`telegram-shopping-bot`, `telegram-workout-bot`, `telegram-planing-bot`). The owner migrates them;
no deprecation shims. A usage survey of those modules found:

- `CommandContext`: only `getChatId()`, `getCommandUpdate().getFrom()` and `getCurrentUpdate()`
  are called; a `CallbackMessages` helper duplicated in three bots derives the callback message.
  No mutator is called. Tests build contexts with `create(...)`/`forUpdate(...)`.
- Implemented SPIs: `AuthInterceptor`, `TextHandler`, `DocumentHandler`, `ContactHandler`,
  `CommandGuard`, `ErrorHandler`, `ParameterRenderer`. Injected: `MessageExecutor`,
  `OutboundMessageBus`, `CommandMenuService`. Never implemented: `IBotCommand`, `LocaleProvider`,
  `GuardDeniedHandler`, presenters.
- `DynamicParameters` flags `approved`, `initiator`, `scipAnswer` are unused; `refreshContext` is
  used by the workout bot.
- Properties set: `token`, `bot-username`, `language`. `permit-commands` is never set, so the
  default `start` currently lets the shopping bot's `/start` bypass `AuthInterceptor`.
- No group-chat usage.

## Design

### 1. Coordinates

- `groupId` `io.github.baevkir`, `artifactId` `sessionbot-telegram-spring-boot-starter` (matches
  the repository name and Spring's third-party starter convention), version `0.1.0-SNAPSHOT`.
- On JitPack the artifact resolves as `com.github.baevkir:sessionbot-telegram-spring-boot-starter`
  (JitPack ignores the pom `groupId`); on Central as `io.github.baevkir:…`. Only `groupId` differs.
- Property prefix `sessionbot.telegram.*` is unchanged.

### 2. Packages

Root `io.github.baevkir.sessionbot`. Public:

| Package | Types |
|---|---|
| (root) | `CommandContext`, `UpdateWrapper`, `DynamicParameters`, `CommandBuilder`, `AuthInterceptor`, `MessageExecutor`, `OutboundMessageBus`, `InboundUpdateBus`, `ChatUpdateStream` |
| `annotation` | `BotCommand`, `CommandMethod`, `Parameter`, `Rendering`, `RenderingOption` |
| `guard` | `Guarded`, `CommandGuard`, `GuardContext`, `GuardDeniedHandler` |
| `handler` | `TextHandler`, `DocumentHandler`, `ContactHandler` |
| `render` | `ParameterRenderer`, `ParameterRequest`, `ParameterOption`, `CompositeParameterRenderer`, `TextParameterRenderer`, `DateParameterRenderer`, `BooleanParameterRenderer`, `TimeParameterRenderer` |
| `error` | `ErrorHandler`, `BotCommandException`, `BotAuthException`, `BotCommandErrorHandler`, `BotAuthErrorHandler` |
| `i18n` | `LocaleProvider`, `ConfiguredLocaleProvider`, `BotLabels` |
| `menu` | `CommandMenuService` |
| `autoconfigure` | `SessionBotAutoConfiguration`, `SessionBotProperties` |

`internal` (its `package-info.java` states: no compatibility guarantees, may change in any
release): `CommandsSessionBot`, `TelegramUpdateHandler`, `ConversationState`, `ContextState`,
`RegisteredCommand` (was `IBotCommand`), `HelpCommand`, `CommandsFactory`, `CommandsDispatcher`,
`DispatcherBotCommand`, `MethodMatcher`, `MethodDescriptor`, `ParameterDescriptor`,
`InvocationResultResolver`, `MessageDescriptor`, `WireFormat`, `TelegramHtml`, `CommandConstants`,
`GuardResolver`, `CommandGuards`, `CommandMenus`, `HelpGuardDeniedHandler`, `SinkInboundUpdateBus`,
`SinkOutboundMessageBus`, `TelegramClientMessageExecutor`, `ErrorHandlerFactory`. Sub-packages
under `internal` are allowed where they keep files focused.

Replaceable defaults stay replaceable through their public interfaces (`@ConditionalOnMissingBean`
on the interface type or bean name, as today). `HelpCommand` and `CommandsFactory` are no longer
overridable beans; a bot that wants a different reply to unknown or refused commands overrides
`GuardDeniedHandler` or registers a `TextHandler`.

The auto-configuration imports file lists `io.github.baevkir.sessionbot.autoconfigure.SessionBotAutoConfiguration`.

### 3. `CommandContext`

A read-only public interface:

```java
public interface CommandContext {
    String getChatId();
    User getUser();                                  // sender of the command update (as AuthInterceptor left it); null if none
    String getCommand();                             // null for a bare update (text/document/contact outside a command)
    List<String> getAnswers();                       // accumulated + pending answers, unmodifiable
    UpdateWrapper getCommandUpdate();
    Optional<UpdateWrapper> getCurrentUpdate();      // latest update of the conversation
    Optional<MaybeInaccessibleMessage> getCallbackMessage(); // current update's callback message, else the command update's
    DynamicParameters getDynamicParams();

    static CommandContext of(Update update) { … }    // for tests and out-of-band use
}
```

- `getX` naming is kept so the calls the bots make today compile unchanged.
- `getUser()` follows today's `getCommandUpdate().getFrom()`, falling back to the current update's
  sender for a bare update.
- `of(Update)` wraps the update and returns a context for it: a command update gives an open
  command context, anything else a bare-update context.
- Removed from the public type: `close`, `startProgress`, `addAnswer`, `addUpdate`,
  `addQuestionMessage`, `getState`, `getUpdates`, `getQuestionMessages`, `getPendingArguments`,
  `isEmpty`, `getInitialUpdate`, `create`, `forUpdate`, `empty`, and Lombok `@ToString`.

`internal.ConversationState implements CommandContext` holds the fold state: command update,
update list, answers, question messages and `ContextState`, plus the mutators. A chat is processed
strictly sequentially (`concatMap` inside its own group), so it uses plain collections, not
`synchronizedList`. The pipeline, dispatcher and built-in renderers work with `ConversationState`;
command methods, guards, handlers, `AuthInterceptor`, `GuardDeniedHandler`, `ErrorHandler`s (via the
exceptions) and `ParameterRequest.getContext()` expose `CommandContext`.

`RegisteredCommand.process` takes `ConversationState`, since running a command advances the
conversation.

**Lifecycle fix.** `/help` (whether requested, the unknown-command fallback, or the default guard
denial) and bare-update dispatch now close their context, so the chat's stream completes at once
instead of holding a `flatMap` slot until `chat-idle-ttl`.

### 4. i18n keyed on `User`

```java
public interface LocaleProvider {
    Locale getLocale(User user);   // user may be null
}
```

- `ConfiguredLocaleProvider` ignores the user and returns `sessionbot.telegram.language`.
- `BotLabels.resolve(String text, CommandContext context)` (uses `context.getUser()`) and
  `resolve(String text, User user)`; the `String userName` overload is removed. Typed accessors
  (`skip`, `yes`, `helpTitle`, …) keep taking `CommandContext`; `helpDescription` takes `User`.
- Internal `RegisteredCommand.getDescription(User)` and `CommandMenus.toBotCommands(commands, User)`.
  `CommandMenuService.refresh(String chatId, User user)` is unchanged.
- No built-in `languageCode` provider: a bot that wants one implements `LocaleProvider` in a few
  lines; it can be added later without a break.

### 5. Renames and removals

- `CommandBuilder.scipAnswer(int)` → `skipAnswer(int)`, `DynamicParameters.canScipAnswer(int)` →
  `canSkipAnswer(int)`, wire key `scipAnswer` → `skip`.
- Removed: `CommandBuilder.commandApproved()`, `setInitiator(String)`,
  `DynamicParameters.commandApproved()`, `getInitiator()` and their constants. Generic
  `addParam`/`getParam`/`hasParam` remain. `refreshContext` stays.
- `ParameterRendererFactory` → `CompositeParameterRenderer` (bean name `defaultParameterRenderer`
  unchanged).
- `model.Option` → `render.ParameterOption`, a record `(String value, String label)`;
  `ParameterRequest.getOptions()` returns `List<ParameterOption>`.
- `CommandsSessionBotConfiguration` → `SessionBotAutoConfiguration`,
  `CommandsSessionBotProperties` → `SessionBotProperties`.
- Removed: `BotMethodPresenter`, `AbstractMessagePresenter`.
- `TextHandler` gains `default boolean supports(String text) { return true; }`; the first ordered
  handler that supports the text wins, as for documents and contacts.
- `TextHandler`, `DocumentHandler`, `ContactHandler` return
  `Publisher<? extends PartialBotApiMethod<?>>` (existing implementations still compile).
- `@Parameter` targets `PARAMETER` only. `@Rendering` and `@RenderingOption` get
  `@Retention(RUNTIME)` and `@Target({})`.

### 6. Defaults and group chats

- `sessionbot.telegram.permit-commands` defaults to an empty list: nothing bypasses
  `AuthInterceptor` unless configured.
- A typed command may carry an addressee: `/order@MyBot?buy` parses to command `order`, addressee
  `MyBot`. `UpdateWrapper.getAddressee()` exposes it (`Optional<String>`). `TelegramUpdateHandler`
  processes a command whose addressee is absent or equals `bot-username` (case-insensitive), and
  **ignores** one addressed to another bot — no reply, no `/help`, context unchanged. Callback data
  never carries an addressee.

### 7. Wire parser

`MessageDescriptor` is rewritten as a single left-to-right parser over the grammar

```
wire    := ["/" command ["@" addressee]] ["?"] answers ["#" params]
answers := answer ("&" answer)*
params  := param ("&" param)*          param := key [":" value]
```

(the `?` is present only after a command). Behavior preserved from subproject A: percent-decoding
of reserved characters, typed text as one verbatim answer, typed commands without dynamic params,
last repeated key wins. Fixed: a lone `#` no longer throws, and `/a?b?c` keeps `b?c` as the answer
instead of dropping `c`.

## Error handling

Unchanged from subproject A, except that error paths now carry `CommandContext` (the interface):
`BotCommandException.getContext()` and `BotAuthException.getContext()` return `CommandContext`.

## Testing

- Every existing test moves to the new packages and types and keeps its assertion, except the
  deliberate behavior changes above.
- New tests:
  - `getCallbackMessage()` prefers the current update's callback message over the command's.
  - `CommandContext.of(Update)` for a command update and for a bare update.
  - `LocaleProvider` receives the `User` (and `null` for out-of-band resolution).
  - A command addressed to this bot runs; one addressed to another bot is ignored.
  - Parser grammar cases, including `#`, `/a?b?c`, `@addressee` with and without answers.
  - `/help` and a bare-update dispatch complete the chat stream immediately.
  - `permit-commands` defaults to empty, so `/start` goes through `AuthInterceptor`.
  - Auto-configuration activates under the new class name and imports entry.
- After implementation: build `family-iot` against the local `0.1.0-SNAPSHOT` to confirm the
  migration list is complete (read-only; the owner migrates the bots).

## Migration

Written to `CHANGELOG.md` under `0.1.0`:

1. Dependency coordinates and imports (`com.kb.sessionbot` → `io.github.baevkir.sessionbot`,
   see package table).
2. Replace `CallbackMessages` helpers with `context.getCallbackMessage()`.
3. Tests: `CommandContext.create(...)`/`forUpdate(...)` → `CommandContext.of(update)`.
4. Add `sessionbot.telegram.permit-commands: [start]` where `/start` must bypass auth (shopping bot).
5. `@MockitoBean CommandsSessionBot` is unnecessary when the token is empty; if kept, import it
   from `internal`.
6. Tests touching `MethodMatcher`, `MessageDescriptor` or `GuardResolver` now depend on
   `internal` (no compatibility guarantee) — prefer asserting through the public API.

## Documentation

- README rewritten for the new coordinates, packages and API (installation via JitPack).
- `CLAUDE.md` brought in line with the code (it still describes `BotCommandResult`,
  `@RenderingMethod` and a `createChild` renderer hierarchy, none of which exist).
- LICENSE, CHANGELOG release entry details and JitPack publishing belong to subproject E.

## Out of scope

- Dependency hygiene (Guava, commons-collections4, Lombok scope, Jackson): subproject C.
- `MessageExecutor` failure contract (log-and-`null`): unchanged.
- Chat concurrency ceiling beyond the lifecycle fix: documented, not redesigned.
