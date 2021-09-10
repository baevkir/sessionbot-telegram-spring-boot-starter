package com.kb.sessionbot.errors.exception;

import com.kb.sessionbot.model.CommandContext;
import lombok.Getter;

@Getter
public class BotCommandException extends RuntimeException {
    private CommandContext context;

    public BotCommandException(CommandContext context, Throwable cause) {
        super(cause);
        this.context = context;
    }
}
