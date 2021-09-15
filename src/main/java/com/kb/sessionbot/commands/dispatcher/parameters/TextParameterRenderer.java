package com.kb.sessionbot.commands.dispatcher.parameters;

import com.kb.sessionbot.commands.CommandBuilder;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

public class TextParameterRenderer implements ParameterRenderer {
    @Override
    public Publisher<? extends PartialBotApiMethod<?>> render(ParameterRequest parameterRequest) {
        return Mono.fromSupplier(() -> {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(parameterRequest.getContext().getChatId());
            sendMessage.setText(parameterRequest.getText());
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
            if (!isEmpty(parameterRequest.getOptions())) {
                List<InlineKeyboardButton> rowInline = parameterRequest.getOptions().stream()
                    .map(option -> InlineKeyboardButton.builder().text(option).callbackData(option).build())
                    .collect(Collectors.toList());

                rowsInline.add(rowInline);
            }
            if (!parameterRequest.isRequired()) {
                rowsInline.add(
                    Collections.singletonList(InlineKeyboardButton.builder()
                        .text("Пропусить")
                        .callbackData(CommandBuilder.create().scipAnswer().build())
                        .build())
                );
            }
            if (isNotEmpty(rowsInline)) {
                sendMessage.setReplyMarkup(InlineKeyboardMarkup.builder().keyboard(rowsInline).build());
            }
            return sendMessage;
        });
    }
}
