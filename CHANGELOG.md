# Changelog

## 0.1.0 — unreleased

First public release.

### Breaking changes and migration

- **Coordinates and packages.** The artifact is `sessionbot-telegram-spring-boot-starter`
  (`com.github.baevkir` on JitPack, `io.github.baevkir` on Maven Central), and every package moved
  from `com.kb.sessionbot` to `io.github.baevkir.sessionbot`:

  | Types | Package |
  |---|---|
  | `CommandContext`, `UpdateWrapper`, `DynamicParameters`, `CommandBuilder`, `AuthInterceptor`, `MessageExecutor`, `OutboundMessageBus`, `InboundUpdateBus`, `ChatUpdateStream` | `io.github.baevkir.sessionbot` |
  | `@BotCommand`, `@CommandMethod`, `@Parameter`, `@Rendering`, `@RenderingOption` | `…sessionbot.annotation` |
  | `@Guarded`, `CommandGuard`, `GuardContext`, `GuardDeniedHandler` | `…sessionbot.guard` |
  | `TextHandler`, `DocumentHandler`, `ContactHandler` | `…sessionbot.handler` |
  | `ParameterRenderer`, `ParameterRequest`, `ParameterOption`, built-in renderers | `…sessionbot.render` |
  | `ErrorHandler`, `BotCommandException`, `BotAuthException`, default error handlers | `…sessionbot.error` |
  | `LocaleProvider`, `ConfiguredLocaleProvider`, `BotLabels` | `…sessionbot.i18n` |
  | `CommandMenuService` | `…sessionbot.menu` |
  | `SessionBotAutoConfiguration`, `SessionBotProperties` | `…sessionbot.autoconfigure` |

  Everything else is in `…sessionbot.internal` and carries no compatibility guarantee.
- **Renamed:** `CommandsSessionBotConfiguration` → `SessionBotAutoConfiguration` and
  `CommandsSessionBotProperties` → `SessionBotProperties` — matters if an app excludes
  auto-configuration by class name (`@SpringBootApplication(exclude = ...)`,
  `spring.autoconfigure.exclude`).
- **Startup now fails fast** on two `@BotCommand` beans sharing an identifier, or one named `help`
  (it clashes with the built-in `/help`).
- **`@Parameter` is allowed on method parameters only** (`@Target(ElementType.PARAMETER)`); it was
  never meant to apply anywhere else.
- **Typed text is no longer split on `&`/`#`.** A typed command's `?`-answers keep today's grammar,
  but `#` inside them is now literal text, not a control-parameter separator — a behavior change, not
  only a fix (see Fixed below).
- **Old skip buttons stop skipping.** Inline keyboards sent before the upgrade with `scipAnswer`
  callback data no longer skip once the bot is upgraded — the wire key is now `skip`. Keyboards
  carrying `approved` or `initiator` still decode (those keys are unchanged); only the dedicated
  accessors were removed (see Removed below) — read them with `hasParam("approved")` /
  `getParam("initiator")`.
- **`CommandContext` is read-only.** `getChatId()`, `getCommandUpdate()` and `getCurrentUpdate()`
  work as before; `getUser()` and `getCallbackMessage()` are new — replace hand-written helpers that
  derive the tapped message from `getCurrentUpdate()`/`getCommandUpdate()` with
  `getCallbackMessage()`. Mutators, `getState()`, `getUpdates()`, `getQuestionMessages()` and
  `getInitialUpdate()` are gone. In tests replace `CommandContext.create(…)`/`forUpdate(…)` with
  `CommandContext.of(update)`.
- **`permit-commands` defaults to empty.** Add `sessionbot.telegram.permit-commands: [start]` if
  `/start` must run before the `AuthInterceptor` recognizes the caller.
- **`LocaleProvider.getLocale(User)`** replaces `getLocale(String userName)`;
  `BotLabels.resolve(text, User)` replaces `resolve(text, String)`.
- **Renamed:** `CommandBuilder.scipAnswer` → `skipAnswer`, `DynamicParameters.canScipAnswer` →
  `canSkipAnswer` (wire key `skip`), `ParameterRendererFactory` → `CompositeParameterRenderer`,
  `Option` → `ParameterOption(value, label)`.
- **Removed:** `CommandBuilder.commandApproved()`/`setInitiator()` and
  `DynamicParameters.commandApproved()`/`getInitiator()` — use `addParam`/`getParam`;
  `BotMethodPresenter`/`AbstractMessagePresenter`; overriding the `HelpCommand` or `CommandsFactory`
  beans.
- **Tests** that mock `CommandsSessionBot` can drop the mock when the token is empty (the bot does
  not start), or import it from `…sessionbot.internal`. Tests touching `MethodMatcher`,
  `MessageDescriptor` or `GuardResolver` now depend on internals.
- **Fewer transitive dependencies.** The library no longer brings Guava, commons-collections4 or
  Lombok onto your classpath (Lombok is `provided`), and it no longer pins commons-lang3 (it still
  arrives, newer, through `telegrambots-meta`). A bot that used any of these without declaring it
  must add it to its own pom.

### Added

- Group chats: `/cmd@ThisBot` runs, `/cmd@OtherBot` is ignored (see README for the current
  limitation).
- `TextHandler.supports(text)` — several text handlers can split the work.
- `CommandContext.getUser()`, `getCallbackMessage()`, `CommandContext.of(update)`.

### Fixed

- Typed text can no longer forge answers or control parameters; `CommandBuilder` escapes reserved
  characters.
- Commands wrapped in AOP proxies (`@Transactional`, …) are dispatched, with their advice.
- Error replies never show exception messages; error handlers match exception subclasses.
- `/help` releases the chat at once; bare updates no longer accumulate in one context.
- An update with no resolvable chat no longer terminates the inbound stream.
- HTML in command descriptions and prompts is escaped.

### Known limitations

- If a bare-update handler fails, the error reply is sent and the chat's stream ends there, so
  further updates already queued for that chat in the same burst are dropped (see README, "Bare
  updates").
- A guarded multi-step command is not safe in a group chat yet: the conversation belongs to the chat,
  not the member, so any member can continue one opened by another under the opener's permissions
  (see README, "Group chats").
