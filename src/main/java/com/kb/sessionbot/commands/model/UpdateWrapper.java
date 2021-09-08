package com.kb.sessionbot.commands.model;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Objects;
import java.util.Optional;

@Slf4j
@Getter
@ToString
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UpdateWrapper {
    private Update update;

    public static UpdateWrapper wrap(Update update) {
        return new UpdateWrapper(Objects.requireNonNull(update, "Update is null."));
    }

    public String getChatId() {
        if (update.hasMessage()) {
            return String.valueOf(update.getMessage().getChatId());
        }
        if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            return String.valueOf(callbackQuery.getMessage());
        }
        log.error("Cannot get chat id from update.{}", update);
        throw new RuntimeException("Cannot get chat id from update");
    }

    public boolean isCommand() {
        return update.hasMessage() && update.getMessage().isCommand();
    }

    public Message getMessage() {
        return update.getMessage();
    }

    public Optional<?> getArgument() {
        if (update.hasMessage()) {
            return Optional.of(getMessage().getText());
        }
        if (update.hasCallbackQuery()) {
            return Optional.of(update.getCallbackQuery().getData());
        }
        return Optional.empty();
    }
}
