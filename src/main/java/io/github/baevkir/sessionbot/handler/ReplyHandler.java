package io.github.baevkir.sessionbot.handler;

import io.github.baevkir.sessionbot.CommandContext;
import reactor.core.publisher.Mono;

/**
 * Claims a plain text message the bot was waiting for, before any {@link TextHandler} sees it.
 * The text is a plain message sent outside any command flow and is passed verbatim. An empty
 * result means the text is not this handler's and the next one is asked.
 */
public interface ReplyHandler {

    Mono<Reply> claim(CommandContext context, String text);
}
