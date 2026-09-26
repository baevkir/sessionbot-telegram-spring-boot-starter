package com.kb.sessionbot.contacts;

import com.kb.sessionbot.model.CommandContext;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Contact;

/**
 * Handles a contact card shared outside any command flow. Implemented as Spring beans by the host
 * bot; the first handler whose {@link #supports} matches wins. When no handler matches (or none are
 * registered) the update falls through to the default help behavior.
 */
public interface ContactHandler {

    boolean supports(Contact contact);

    Publisher<PartialBotApiMethod<?>> handle(CommandContext context, Contact contact);
}
