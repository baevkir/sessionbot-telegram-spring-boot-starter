package com.kb.sessionbot.commands;

import com.kb.sessionbot.commands.model.CommandRequest;
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
     * @return the true if bot command should not show in help
     */
    default boolean hidden() {
        return false;
    }

    /**
     * Process the message
     * @return
     */
    Mono<? extends PartialBotApiMethod<?>> process(CommandRequest commandRequest);
}
