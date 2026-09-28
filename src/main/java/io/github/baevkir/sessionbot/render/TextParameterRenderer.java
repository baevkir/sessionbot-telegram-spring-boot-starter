package io.github.baevkir.sessionbot.render;

import io.github.baevkir.sessionbot.CommandBuilder;
import io.github.baevkir.sessionbot.i18n.BotLabels;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.util.CollectionUtils.isEmpty;

public class TextParameterRenderer implements ParameterRenderer {
    private final BotLabels labels;

    public TextParameterRenderer(BotLabels labels) {
        this.labels = labels;
    }

    @Override
    public Publisher<? extends PartialBotApiMethod<?>> render(ParameterRequest parameterRequest) {
        return Mono.fromSupplier(() -> {
            var messageBuilder = SendMessage.builder()
                .chatId(parameterRequest.getContext().getChatId())
                .text(parameterRequest.getText())
                .parseMode(ParseMode.HTML);

            List<InlineKeyboardRow> rowsInline = new ArrayList<>();
            if (!isEmpty(parameterRequest.getOptions())) {
                InlineKeyboardRow rowInline = parameterRequest.getOptions().stream()
                    .map(option ->
                        InlineKeyboardButton.builder()
                            .text(labels.resolve(option.label(), parameterRequest.getContext()))
                            .callbackData(CommandBuilder.create().addAnswer(option.value()).build())
                            .build()
                    )
                    .collect(Collectors.toCollection(InlineKeyboardRow::new));

                rowsInline.add(rowInline);
            }
            if (!parameterRequest.isRequired()) {
                rowsInline.add(
                    new InlineKeyboardRow(InlineKeyboardButton.builder()
                        .text(labels.skip(parameterRequest.getContext()))
                        .callbackData(CommandBuilder.create().skipAnswer(parameterRequest.getIndex()).build())
                        .build())
                );
            }
            if (!rowsInline.isEmpty()) {
                messageBuilder.replyMarkup(InlineKeyboardMarkup.builder().keyboard(rowsInline).build());
            }
            return messageBuilder.build();
        });
    }
}