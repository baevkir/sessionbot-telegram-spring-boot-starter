package com.kb.sessionbot.commands.dispatcher.parameters;

import com.kb.sessionbot.commands.CommandBuilder;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

public class BooleanParameterRenderer implements ParameterRenderer {
    @Override
    public Publisher<? extends PartialBotApiMethod<?>> render(ParameterRequest parameterRequest) {
        return Mono.fromSupplier(() -> {
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
            List<InlineKeyboardButton> rowInline = Arrays.asList(
                InlineKeyboardButton.builder().text("Да").callbackData(Boolean.toString(true)).build(),
                InlineKeyboardButton.builder().text("Нет").callbackData(Boolean.toString(false)).build()
                );

            rowsInline.add(rowInline);

            if (!parameterRequest.isRequired()) {
                rowsInline.add(
                    Collections.singletonList(InlineKeyboardButton.builder()
                        .text("Пропусить")
                        .callbackData(CommandBuilder.create().scipAnswer().build())
                        .build())
                );
            }
            return SendMessage.builder()
                .chatId(parameterRequest.getContext().getChatId())
                .text(parameterRequest.getText())
                .parseMode(ParseMode.HTML)
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rowsInline).build())
                .build();
        });
    }
}
