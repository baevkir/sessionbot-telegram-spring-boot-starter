package com.kb.sessionbot.errors.exception;

import com.kb.sessionbot.model.CommandContext;
import lombok.Getter;

@Getter
public class BotAuthException extends RuntimeException {
    private final CommandContext context;

    public BotAuthException(CommandContext context, String message) {
        super(message);
        this.context = context;
    }
}
