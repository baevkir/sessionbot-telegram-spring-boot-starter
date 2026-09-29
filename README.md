# sessionbot-telegram-spring-boot-starter

[![JitPack](https://jitpack.io/v/baevkir/sessionbot-telegram-spring-boot-starter.svg)](https://jitpack.io/#baevkir/sessionbot-telegram-spring-boot-starter)

A Spring Boot auto-configuration starter for building Telegram bots whose commands behave like
multi-step conversations. A command can ask the caller for missing arguments one at a time, over
inline keyboards or plain text replies, and the framework folds the accumulated answers into a
per-chat session until the command has everything it needs to run.

## Requirements

- Java 25
- Spring Boot 4.1

The Telegram client (`org.telegram:telegrambots-*` 10) comes with the starter; you do not declare it.

## Installation

Releases are published on [JitPack](https://jitpack.io/#baevkir/sessionbot-telegram-spring-boot-starter).
The version is the git tag, `v` included.

**Maven** — add the JitPack repository and the dependency:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.baevkir</groupId>
        <artifactId>sessionbot-telegram-spring-boot-starter</artifactId>
        <version>v0.1.0</version>
    </dependency>
</dependencies>
```

**Gradle:**

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.baevkir:sessionbot-telegram-spring-boot-starter:v0.1.0")
}
```

**A local build of `master`** — to try unreleased changes, install the snapshot into your local
Maven repository and depend on the project's own coordinates (no JitPack repository needed):

```bash
git clone https://github.com/baevkir/sessionbot-telegram-spring-boot-starter.git
cd sessionbot-telegram-spring-boot-starter
mvn install -DskipTests      # needs JDK 25
```

```xml
<dependency>
    <groupId>io.github.baevkir</groupId>
    <artifactId>sessionbot-telegram-spring-boot-starter</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>
```

A snapshot only exists on the machine that installed it, so CI and Docker builds should use a
JitPack release.

Java packages live under `io.github.baevkir.sessionbot`; everything under
`io.github.baevkir.sessionbot.internal` is implementation detail with no compatibility guarantee.
Upgrading from the pre-release `com.kb:telegram-session-bot`? See the migration notes in
[CHANGELOG.md](CHANGELOG.md).

## Quick start

Set the two properties that activate the auto-configuration:

```yaml
sessionbot:
  telegram:
    token: ${BOT_TOKEN}
    bot-username: ${BOT_USERNAME}
```

Declare a command as a `@BotCommand` bean (the annotation is itself a `@Component`, so a normal
component scan picks it up) with one `@CommandMethod`:

```java
@BotCommand(value = "ping", description = "Replies with pong")
public class PingCommand {

    @CommandMethod
    public SendMessage ping(String chatId) {
        return SendMessage.builder().chatId(chatId).text("pong").build();
    }
}
```

The bot now answers `/ping` with "pong".

## Parameters

A command method can require one or more answers, matched against a literal-and-`{placeholder}`
template declared on `@CommandMethod(arguments = ...)`:

```java
@BotCommand("order")
public class OrderCommand {

    @CommandMethod(arguments = "buy&{product}")
    public Mono<SendMessage> buy(@Parameter("product") String product) {
        return Mono.just(SendMessage.builder().chatId("...").text("buying " + product).build());
    }
}
```

When an answer is missing, the matching `ParameterRenderer` prompts for it (plain text, a date
picker, a yes/no keyboard, ...) and the command suspends until the reply arrives. The in-progress
state lives in a per-chat `CommandContext`, held in memory only: it does **not** survive a JVM
restart, and an idle chat's context is evicted after `sessionbot.telegram.chat-idle-ttl`. A bot that
needs durable multi-step state keeps it in its own storage.

Inline-keyboard buttons carry their target command as a wire-format string built with
`CommandBuilder`:

```java
InlineKeyboardButton.builder()
    .text("Buy milk")
    .callbackData(CommandBuilder.create().command("order").addAnswer("buy").addAnswer("milk").build())
    .build();
```

Telegram caps callback data at 64 bytes; `CommandBuilder.build()` only logs a warning when the
result exceeds that limit, so keep command and answer strings short. The builder escapes the
characters the format reserves — `% ? & #` in answers, and additionally `:` in dynamic parameters
(where it splits key from value) — so an answer or parameter value round-trips through those
characters. Two exceptions: an answer-only button (no `command(...)`) whose first answer starts with
`/` is read back as a command, not as answers, since a leading `/` is not itself escaped; and trailing
empty answers are dropped on the way back in, since they are joined and split with the same `&`
separator.

Only button presses are read as wire format. Text the user types while a command waits for input is
taken as one answer, verbatim — `Tom & Jerry` stays a single value — and a typed command such as
`/order?buy` may carry answers but never the control parameters (`#...`) a button can.

### The conversation: `CommandContext`

A command method, guard, handler or renderer can take a `CommandContext` — a read-only view of the
chat's conversation: `getChatId()`, `getUser()`, `getCommand()`, `getAnswers()`,
`getCommandUpdate()` (the command, or the bare update itself outside a command),
`getCurrentUpdate()`, `getCallbackMessage()` (the message whose button was tapped) and
`getDynamicParams()`. Only the library advances the conversation. In tests, build one with
`CommandContext.of(update)`.

## Bare updates

An update that is not a command — plain text, a shared contact, an uploaded document — is routed to
a bean of the matching handler interface:

- `TextHandler` — plain text sent outside any command flow; the first bean whose `supports(String)`
  matches wins.
- `ContactHandler` — a shared contact; the first bean whose `supports(Contact)` matches wins.
- `DocumentHandler` — an uploaded document; the first bean whose `supports(Document)` matches wins.

With no handler registered, or none matching, the update falls through to the default `/help`
behavior.

**Known limitation:** if a bare-update handler (or the fallback `/help`) fails, the error reply is
sent and the chat's update stream ends there — any further updates already queued for that chat in
the same burst are dropped, not merely delayed. The chat resumes normally on its next incoming update.

## Authentication

`AuthInterceptor.intercept(CommandContext) -> Mono<Boolean>` gates every command before it runs. The
default bean allows everything; a consuming app supplies its own to enforce membership, roles, and
so on:

```java
@Bean
AuthInterceptor authInterceptor(MyUserService users) {
    return context -> users.isKnown(context.getCommandUpdate().getFrom());
}
```

`sessionbot.telegram.permit-commands` (empty by default) lists commands that skip the interceptor
entirely — typically an entry-point command that has to run before the caller can be recognized at
all. It does **not** bypass command guards (below): a permitted command still gets refused if a
guard denies it. A bot whose `/start` must admit unknown callers sets
`permit-commands: [start]`.

## Command guards

A guard restricts a command to callers a `CommandGuard` permits:

```java
public interface CommandGuard {
    Mono<Boolean> permits(GuardContext context);
}
```

Attach a guard to a command directly:

```java
@Guarded(AdminGuard.class)
@BotCommand("admin")
public class AdminCommand { /* ... */ }
```

or name it once as a meta-annotation:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Guarded(AdminGuard.class)
public @interface AdminOnly { }

@AdminOnly
@BotCommand("admin")
public class AdminCommand { /* ... */ }
```

Several `@Guarded` found on one command — direct and/or through meta-annotations — combine with
**AND**, de-duplicated, so a repeated guard is only evaluated once. `CommandGuard`s are resolved as
Spring beans: a command guarded by a type with no matching bean fails application **startup**, not a
later request.

Guards run after the `AuthInterceptor`, and on every update of the command's conversation, not just
the first — so revoking access takes effect on the caller's very next tap. An error or an empty
result from a guard is treated as a denial, the same as returning `false`.

That ordering does not hold for the fallback `/help` that answers a non-command update (plain text,
a sticker, a photo) with no active conversation and no matching handler: that path never runs the
`AuthInterceptor`, so a guard filtering `/help`'s command list sees the raw Telegram sender, not the
name the interceptor would otherwise have normalized it to.

When a guard denies, the configured `GuardDeniedHandler` answers instead of the command running. The
default (`HelpGuardDeniedHandler`) answers as it would to an unknown command — but the conversation
is closed afterwards, so a follow-up plain text message reaches the `TextHandler` instead of
producing `/help` again. Override it for an explicit refusal:

```java
@Bean
GuardDeniedHandler guardDeniedHandler() {
    return context -> Mono.just(SendMessage.builder()
        .chatId(context.getChatId())
        .text("You don't have access to that.")
        .build());
}
```

## Per-chat menus

The command list Telegram shows by default omits every guarded command, since a guard is evaluated
per caller while the default menu is shared by every chat. To give one chat a menu that also lists
the guarded commands its caller may use, call `CommandMenuService`:

```java
commandMenuService.refresh(chatId, user); // sets a chat-scoped menu, or clears it if nothing extra applies
commandMenuService.reset(chatId);         // drops the chat-scoped menu, falling back to the default
```

The library never calls `CommandMenuService` itself — the application decides when a chat's menu
should change (at startup, after a role change, ...). `refresh` evaluates every guard with
`chatType = "private"`, because per-chat menus are meant for private chats; group-chat scopes are
out of scope for now. Telegram clients also cache the command menu, so a change may take a moment to
show; access never depends on the menu, since guards are re-checked on every call regardless of what
the menu currently displays.

## Group chats

In a group, Telegram delivers commands as `/order@MyBot`. The bot runs a command addressed to its
own `bot-username` (case-insensitive) or to no one, and ignores one addressed to another bot — no
reply, no `/help`.

A conversation belongs to the chat, not to the member who is typing: in a group, any member's reply
or button tap continues the open conversation, and the `AuthInterceptor` and command guards judge the
member who opened it, not whoever answers. A guarded multi-step command is therefore **not safe in
groups yet** — any group member can continue (and complete) a conversation another member started
under the permissions of whoever opened it.

## Configuration reference

| Property | Default | Description |
|---|---|---|
| `sessionbot.telegram.token` | *(required)* | The bot's Telegram API token. |
| `sessionbot.telegram.bot-username` | *(required)* | The bot's Telegram username. |
| `sessionbot.telegram.language` | `en` | Bot-wide language tag for built-in labels (prompts, `/help` chrome, ...). |
| `sessionbot.telegram.chat-idle-ttl` | `30m` | Idle period after which an inactive chat's update stream is released. |
| `sessionbot.telegram.max-concurrent-chats` | `256` | Maximum number of chats processed concurrently (per-chat fan-out). |
| `sessionbot.telegram.permit-commands` | *(empty)* | Commands that run without consulting the `AuthInterceptor` (see Authentication above). |

Setting `token` and `bot-username` is what activates the auto-configuration at all; every other
property is inert until then.

## Overriding beans

Nearly every bean `SessionBotAutoConfiguration` declares is `@ConditionalOnMissingBean`, so a
consuming app overrides any of them by simply declaring its own bean of the same type — or, for the
name-qualified ones below, the same bean name:

- By type: `TelegramClient`, `TelegramBotsLongPollingApplication`, `MessageExecutor`,
  `OutboundMessageBus`, `GuardDeniedHandler`, `CommandMenuService`, `InboundUpdateBus`,
  `AuthInterceptor`, `LocaleProvider`, `BotLabels`. (`HelpCommand` and `CommandsFactory` are
  internal and no longer overridable beans; to change the reply to unknown or refused commands
  override `GuardDeniedHandler`, or handle plain text with a `TextHandler`.)
- By name: the built-in `ParameterRenderer`s (`defaultParameterRenderer`, `textParameterRenderer`,
  `booleanParameterRenderer`, `dateParameterRenderer`, `timeParameterRenderer`), the default
  `ErrorHandler`s (`botCommandErrorHandler`, `botAuthErrorHandler`) and
  `sessionbotLabelsMessageSource` (the `MessageSource` backing built-in labels).

When a command throws, the default `botCommandErrorHandler` logs the error and replies with a
generic, localized "something went wrong" — never the exception's own message, which may expose
internals. To show a user a specific reply, declare an `ErrorHandler<YourException>` bean; a handler
also covers subclasses of its exception type, and the most specific one wins.

A `CommandGuard` is not one of these beans — it is resolved by the type named in `@Guarded`, so it
only needs to exist, under any bean name.

## License

MIT — see [LICENSE](LICENSE).
