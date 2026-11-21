# ReplyHandler: a bot can wait for a typed answer

Date: 2026-11-21
Status: implemented on `feature/reply-handler`
Target: 0.2.0-SNAPSHOT (additive, no breaking change)

## Problem

A bot sometimes asks a person something outside any command and waits for the answer as plain
text: the name of a new shopping list, a dish name, a reply to a question an AI model asked about a
meal photo. Whether the bot is waiting is state in the bot's own database, keyed by chat or user.

`TextHandler` cannot express that. Its `supports(String text)` is synchronous and sees only the
text, so a handler cannot look up "am I waiting for this person". Its `handle` returns a publisher
of messages, so "this text is not mine" and "I took it and have nothing to say" look the same.

The shopping bot works around both in one 250-line `TextHandler` that chains every pending intent
by hand and wraps results in `Optional` to tell the two cases apart. A second bot (the workout
bot, for meal questions) would have to copy that.

## Goal

One seam in the library that lets a bean claim a bare text message reactively, with the full
update context, and say clearly whether it took the message.

Not goals:

- The library stores no pending state and knows nothing about questions, expiry or buttons.
- Photos, documents and contacts as answers.
- Moving the shopping bot to the new seam (a later, separate change in family-iot).

## API

Two public types in `io.github.baevkir.sessionbot.handler`.

```java
public interface ReplyHandler {

    /** Empty means the text is not this handler's: the next one is asked. */
    Mono<Reply> claim(CommandContext context, String text);
}
```

```java
public final class Reply {

    public static Reply of(PartialBotApiMethod<?>... messages);

    public static Reply of(List<? extends PartialBotApiMethod<?>> messages);

    /** The text was taken and nothing is sent back. */
    public static Reply silent();

    public List<PartialBotApiMethod<?>> messages();
}
```

`text` is the message text verbatim, exactly as `TextHandler` receives it.

Claiming is one step on purpose. A bot claims a pending answer with one guarded `UPDATE` in its
own table. A separate "do you accept" call followed by "handle" would leave a gap in which two
messages both pass the check.

## Dispatch

`TelegramUpdateHandler.dispatchOutsideCommand` routes a bare update in this order:

1. contact
2. document
3. photo
4. `ReplyHandler` beans
5. `TextHandler` beans
6. the `/help` fallback

Steps 1 to 3 and 5 to 6 are unchanged. Step 4 applies only to an update that carries message
text, and only when at least one `ReplyHandler` bean exists.

Rules for step 4:

- Handlers are asked one at a time in bean order (`@Order`, as the other handler lists).
- The first handler whose `claim` emits a `Reply` wins. Its messages are executed like any bare
  handler's result. No later `ReplyHandler` and no `TextHandler` sees that text.
- When every handler answers empty, the text goes on to step 5 exactly as today.
- A `ReplyHandler` is never asked about a photo caption, a callback, or text typed while a command
  conversation is open. Text typed during an open command is that command's answer, as today.

### Authorization

The `AuthInterceptor` runs once, before the first `claim`. A refused sender gets the same
`BotAuthException` a `TextHandler` dispatch raises, and no `claim` is called.

When the text then falls through to a `TextHandler`, the interceptor is not run a second time for
that update.

One visible consequence: in a bot that registers a `ReplyHandler`, an unauthorized sender's plain
text now always ends in the auth refusal. Before, text that no `TextHandler` supported reached the
`/help` fallback, which never runs the interceptor. A bot with no `ReplyHandler` bean behaves
exactly as before.

### Errors

An error from `claim` is mapped like any bare handler's error: `BotCommandException` and
`BotAuthException` pass through, anything else is wrapped in `BotCommandException`. An error does
not fall through to the next handler.

## Internals

- `dispatchOutsideCommand` returns `Optional<Flux<…>>` today, decided synchronously. It becomes
  reactive for the text branch; contact, document and photo keep their synchronous match.
- `TelegramUpdateHandler` gets one more constructor that takes `List<ReplyHandler>`. The existing
  constructors stay and pass an empty list, so tests in consuming projects that build the handler
  directly keep compiling.
- `SessionBotAutoConfiguration` collects `ObjectProvider<ReplyHandler>` with `orderedStream()`,
  like the other handler lists.

## What a bot does with it

The workout bot's meal question, as the first user:

- When the model asks a question, the bot stores it on the meal row (who, which meal, until when)
  and sends it with answer buttons.
- A button tap is an ordinary `@BotCommand` callback.
- A typed answer reaches the bot's `ReplyHandler`. Its `claim` runs one guarded `UPDATE … WHERE
  the question is open and not expired RETURNING …`. A returned row means the text was the answer:
  the handler emits `Reply.of(…)` or `Reply.silent()`. No row means empty, and the text goes on to
  the bot's `TextHandler` beans.

Expiry, the one-question-per-photo rule and deleting the typed answer are all the bot's business.

## Tests

In `TelegramUpdateHandlerTest` and `TelegramUpdateHandlerAuthTest`:

- a claimed text executes the reply's messages and reaches no `TextHandler`;
- `Reply.silent()` sends nothing and reaches no `TextHandler` and no `/help`;
- an empty `claim` passes the text to the next `ReplyHandler`, then to the `TextHandler`, then to
  `/help`;
- handlers are asked in order, and a handler after the winner is never asked;
- a photo with a caption, and text typed during an open command, never reach a `ReplyHandler`;
- a refused sender gets the auth error and `claim` is not called;
- the interceptor runs once when the text falls through to a `TextHandler`;
- an error in `claim` surfaces as `BotCommandException` and does not fall through;
- with no `ReplyHandler` bean, an unauthorized sender's unsupported text still reaches `/help`.

One auto-configuration test: a `ReplyHandler` bean in the context is the one the handler asks.

## Documentation

- README, "Bare updates": the new interface, the order, and the authorization consequence above
  (the paragraph about the `/help` fallback not running the interceptor gets the exception).
- CHANGELOG, 0.2.0 "Added".
- Javadoc on both types, two sentences each.

## Rollout

Merge to the library's `master`, `mvn install` the snapshot locally. Nothing in family-iot changes
until the meal-logging work adds the first `ReplyHandler` bean; CI there builds the library from
`master`, so the library PR must be merged before that PR goes green.
