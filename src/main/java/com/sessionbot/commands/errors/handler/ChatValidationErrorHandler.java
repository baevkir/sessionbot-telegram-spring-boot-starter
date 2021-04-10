package com.sessionbot.commands.errors.handler;

import com.sessionbot.commands.errors.exception.validation.ChatValidationException;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

@Slf4j
public class ChatValidationErrorHandler implements ErrorHandler<ChatValidationException> {
    @Override
    public Mono<? extends BotApiMethod<?>> handle(ChatValidationException exception) {
        Long chatId = exception.getErrorData().getCommandRequest().getCommandMessage().getChatId();
        return Mono.fromSupplier(() -> {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(String.valueOf(chatId));
            sendMessage.setText(exception.getMessage());

            if (!isEmpty(exception.getErrorData().getOptions())) {
                InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
                List<InlineKeyboardButton> rowInline = exception.getErrorData().getOptions().stream()
                        .map(option ->
                                InlineKeyboardButton.builder().text(option).callbackData(option).build())
                        .collect(Collectors.toList());

                rowsInline.add(rowInline);
                markupInline.setKeyboard(rowsInline);
                sendMessage.setReplyMarkup(markupInline);
            }
            return sendMessage;
        });
    }
}
