package com.kb.sessionbot.guard;

import com.kb.sessionbot.model.CommandContext;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;

/**
 * Answers a caller a {@link CommandGuard} refused. The default answers as for an unknown command, so a
 * guarded command's existence is not revealed; declare your own bean for an explicit "no access" reply.
 */
@FunctionalInterface
public interface GuardDeniedHandler {
    Publisher<? extends PartialBotApiMethod<?>> onDenied(CommandContext context);
}
