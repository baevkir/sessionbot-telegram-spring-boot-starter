package com.kb.sessionbot.model;

import com.kb.sessionbot.commands.CommandConstants;
import com.kb.sessionbot.commands.MessageDescriptor;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.*;

import static com.kb.sessionbot.commands.CommandConstants.COMMAND_START;
import static com.kb.sessionbot.commands.CommandConstants.DYNAMIC_PARAMETERS_SEPARATOR;

@Slf4j
@Getter
@ToString
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UpdateWrapper {
    private Update update;
    private MessageDescriptor messageDescriptor;

    public static UpdateWrapper wrap(Update update) {
        return new UpdateWrapper(
            Objects.requireNonNull(update, "Update is null."),
            MessageDescriptor.parse(getText(update).orElse(""))
        );
    }

    public String getChatId() {
        if (update.hasMessage()) {
            return String.valueOf(update.getMessage().getChatId());
        }
        if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            return String.valueOf(callbackQuery.getMessage().getChatId());
        }
        log.error("Cannot get chat id from update.{}", update);
        throw new RuntimeException("Cannot get chat id from update");
    }

    public boolean isCommand() {
        return (update.hasMessage() && update.getMessage().isCommand()) || messageDescriptor.isCommand();
    }

    public String getCommand() {
        return messageDescriptor.getCommand();
    }

    public boolean needRefreshContext() {
        return messageDescriptor.needRefreshContext();
    }

    public boolean scipAnswer() {
        return messageDescriptor.canScipAnswer();
    }

    public User getFrom() {
        return Optional.ofNullable(update.getMessage())
            .map(Message::getFrom)
            .or(() -> Optional.ofNullable(update.getCallbackQuery()).map(CallbackQuery::getFrom))
            .orElse(null);
    }

    public List<String> getAnswers() {
        return messageDescriptor.getAnswers();
    }

    public Optional<Message> getCallbackMessage() {
        return Optional.of(update)
            .filter(Update::hasCallbackQuery)
            .map(Update::getCallbackQuery)
            .map(CallbackQuery::getMessage);
    }

    public Map<String, String> getDynamicParams() {
        return messageDescriptor.getDynamicParams();
    }

    private static Optional<String> getText(Update update) {
        if (update.hasMessage()) {
            return Optional.of(update.getMessage().getText());
        }
        if (update.hasCallbackQuery()) {
            return Optional.of(update.getCallbackQuery().getData());
        }
        return Optional.empty();
    }
}
