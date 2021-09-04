package com.kb.sessionbot.commands.dispatcher.parameters;

import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

public class DefaultParameterRenderer implements ParameterRenderer{
    @Override
    public Mono<? extends PartialBotApiMethod<?>> render(ParameterRequest parameterRequest) {
        Long chatId = parameterRequest.getCommandRequest().getContext().getCommandMessage().getChatId();
        return Mono.fromSupplier(() -> {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(String.valueOf(chatId));
            sendMessage.setText(parameterRequest.getText());

            if (!isEmpty(parameterRequest.getOptions())) {
                InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
                List<InlineKeyboardButton> rowInline = parameterRequest.getOptions().stream()
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
