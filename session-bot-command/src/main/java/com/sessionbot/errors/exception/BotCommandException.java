package com.sessionbot.errors.exception;

import com.sessionbot.CommandRequest;
import lombok.Getter;

@Getter
public class BotCommandException extends RuntimeException{
    private CommandRequest commandRequest;

    public BotCommandException(CommandRequest commandRequest, Throwable cause) {
        super(cause);
        this.commandRequest = commandRequest;
    }
}
