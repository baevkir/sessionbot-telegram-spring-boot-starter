package com.kb.sessionbot.commands.errors.exception;

import lombok.Getter;
import org.telegram.telegrambots.meta.api.objects.Update;

@Getter
public class BotAuthException extends RuntimeException {
    private String chatId;
    public BotAuthException(String chatId, String message) {
        super(message);
        this.chatId = chatId;
    }
}
