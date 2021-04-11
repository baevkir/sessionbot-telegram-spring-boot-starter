package com.kb.sessionbot.commands;

import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import reactor.core.publisher.Mono;

public interface IBotCommand {
    /**
     * Get the identifier of this command
     *
     * @return the identifier
     */
    String getCommandIdentifier();

    /**
     * Get the description of this command
     *
     * @return the description as String
     */
    String getDescription();

    /**
     * Process the message
     * @return
     */
    Mono<? extends PartialBotApiMethod<?>> process(CommandRequest commandRequest);
}
