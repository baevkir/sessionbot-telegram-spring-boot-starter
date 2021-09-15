package com.kb.sessionbot.model;

import com.kb.sessionbot.commands.CommandBuilder;
import com.kb.sessionbot.commands.CommandParser;
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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.kb.sessionbot.commands.CommandConstants.COMMAND_START;
import static com.kb.sessionbot.commands.CommandConstants.DYNAMIC_PARAMETERS_SEPARATOR;

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
            return String.valueOf(callbackQuery.getMessage().getChatId());
        }
        log.error("Cannot get chat id from update.{}", update);
        throw new RuntimeException("Cannot get chat id from update");
    }

    public boolean isCommand() {
        return (update.hasMessage() && update.getMessage().isCommand()) ||
            getText().map(text -> text.startsWith(COMMAND_START)).orElse(false);
    }

    public boolean needRefreshContext() {
        return getDynamicParams().containsKey(CommandBuilder.REFRESH_CONTEXT_PARAM);
    }

    public boolean scipAnswer() {
        return BooleanUtils.toBoolean(getDynamicParams().getOrDefault(CommandBuilder.SCIP_ANSWER_PARAM, "false"));
    }
    public User getFrom() {
        return Optional.ofNullable(update.getMessage())
            .map(Message::getFrom)
            .or(() -> Optional.ofNullable(update.getCallbackQuery()).map(CallbackQuery::getFrom))
            .orElse(null);
    }

    public Optional<String> getArguments() {
        return getText().filter(text -> !text.startsWith(DYNAMIC_PARAMETERS_SEPARATOR));
    }

    public Optional<Message> getCallbackMessage() {
        return Optional.of(update)
            .filter(Update::hasCallbackQuery)
            .map(Update::getCallbackQuery)
            .map(CallbackQuery::getMessage);
    }

    public Map<String, String> getDynamicParams() {
        return getText()
            .map(text -> CommandParser.create(text).parseDynamicParams())
            .orElse(Collections.emptyMap());
    }

    public Optional<String> getText() {
        if (update.hasMessage()) {
            return Optional.of(update.getMessage().getText());
        }
        if (update.hasCallbackQuery()) {
            return Optional.of(update.getCallbackQuery().getData());
        }
        return Optional.empty();
    }
}
