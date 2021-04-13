package com.kb.sessionbot.commands.errors.exception;

import com.kb.sessionbot.commands.CommandRequest;
import lombok.Getter;

@Getter
public class BotCommandException extends RuntimeException {
    private CommandRequest commandRequest;

    public BotCommandException(CommandRequest commandRequest, Throwable cause) {
        super(cause);
        this.commandRequest = commandRequest;
    }
}
