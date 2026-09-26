package io.github.baevkir.sessionbot.handler;

import io.github.baevkir.sessionbot.CommandContext;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;

/**
 * Handles a plain text message sent outside any command flow. Implemented as Spring beans by the
 * host bot; the first handler (in bean order) whose {@link #supports} matches wins, and with none
 * matching the update falls through to the default help behavior. {@code text} is the message text
 * verbatim: unlike a command answer it is not split on {@code &} or {@code #}.
 */
public interface TextHandler {

    default boolean supports(String text) {
        return true;
    }

    Publisher<? extends PartialBotApiMethod<?>> handle(CommandContext context, String text);
}
