package com.kb.sessionbot.text;

import com.kb.sessionbot.model.CommandContext;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;

/**
 * Handles a plain text message sent outside any command flow. Implemented as a Spring bean by the
 * host bot; the first registered handler wins, and with none registered the update falls through to
 * the default help behavior. {@code text} is the message text verbatim: unlike a command answer it is
 * not split on {@code &} or {@code #}.
 */
public interface TextHandler {

    Publisher<PartialBotApiMethod<?>> handle(CommandContext context, String text);
}
