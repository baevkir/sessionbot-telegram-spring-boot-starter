package com.kb.sessionbot.commands.errors.exception;

import com.kb.sessionbot.commands.CommandRequest;
import lombok.Getter;

@Getter
public class BotAuthException extends RuntimeException {
    private final CommandRequest request;

    public BotAuthException(CommandRequest request, String message) {
        super(message);
        this.request = request;
    }
}
