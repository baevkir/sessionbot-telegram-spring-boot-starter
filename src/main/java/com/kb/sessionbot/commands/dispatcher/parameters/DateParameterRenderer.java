package com.kb.sessionbot.commands.dispatcher.parameters;

import com.google.common.collect.ImmutableList;
import com.kb.sessionbot.commands.CommandBuilder;
import com.kb.sessionbot.model.UpdateWrapper;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static java.time.format.DateTimeFormatter.ISO_DATE;

public class DateParameterRenderer implements ParameterRenderer {
    private static final String DATE_PROPERTY = "date-renderer-date";
    private static final String CONTINUE_CHOOSE = "date-renderer-continue";

    @Override
    public Publisher<? extends PartialBotApiMethod<?>> render(ParameterRequest parameterRequest) {
        return Mono.fromSupplier(() -> {
            var date = Optional.ofNullable(parameterRequest.getContext().getDynamicParams().get(DATE_PROPERTY))
                .map(LocalDate::parse)
                .orElseGet(LocalDate::now)
                .withDayOfMonth(1);

            var calbackMessage = parameterRequest.getContext().getCurrentUpdate().flatMap(UpdateWrapper::getCallbackMessage).orElse(null);
            if (parameterRequest.getContext().getDynamicParams().containsKey(CONTINUE_CHOOSE) && calbackMessage != null) {
                return EditMessageReplyMarkup.builder()
                    .chatId(parameterRequest.getContext().getChatId())
                    .messageId(calbackMessage.getMessageId())
                    .replyMarkup(buildKeyBoard(date))
                    .build();
            }
            return SendMessage
                .builder()
                .chatId(parameterRequest.getContext().getChatId())
                .text(String.format("%s (Формат: %s)", parameterRequest.getText(), LocalDate.now().format(ISO_DATE)))
                .replyMarkup(buildKeyBoard(date))
                .build();
        });
    }

    private InlineKeyboardMarkup buildKeyBoard(LocalDate date) {
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        rowsInline.add(Collections.singletonList(
            InlineKeyboardButton.builder()
                .text(date.format(DateTimeFormatter.ofPattern("MMMM yyyy")))
                .callbackData(CommandBuilder.create().addParam(DATE_PROPERTY, date.format(ISO_DATE)).addParam(CONTINUE_CHOOSE).build())
                .build()
        ));

        rowsInline.add(Arrays.asList(
            InlineKeyboardButton.builder()
                .text("Пн")
                .callbackData(CommandBuilder.create().addParam(DATE_PROPERTY, date.format(ISO_DATE)).addParam(CONTINUE_CHOOSE).build())
                .build(),
            InlineKeyboardButton.builder()
                .text("Вт")
                .callbackData(CommandBuilder.create().addParam(DATE_PROPERTY, date.format(ISO_DATE)).addParam(CONTINUE_CHOOSE).build())
                .build(),
            InlineKeyboardButton.builder()
                .text("Ср")
                .callbackData(CommandBuilder.create().addParam(DATE_PROPERTY, date.format(ISO_DATE)).addParam(CONTINUE_CHOOSE).build())
                .build(),
            InlineKeyboardButton.builder()
                .text("Чт")
                .callbackData(CommandBuilder.create().addParam(DATE_PROPERTY, date.format(ISO_DATE)).addParam(CONTINUE_CHOOSE).build())
                .build(),
            InlineKeyboardButton.builder()
                .text("Пт")
                .callbackData(CommandBuilder.create().addParam(DATE_PROPERTY, date.format(ISO_DATE)).addParam(CONTINUE_CHOOSE).build())
                .build(),
            InlineKeyboardButton.builder()
                .text("Сб")
                .callbackData(CommandBuilder.create().addParam(DATE_PROPERTY, date.format(ISO_DATE)).addParam(CONTINUE_CHOOSE).build())
                .build(),
            InlineKeyboardButton.builder()
                .text("Вс")
                .callbackData(CommandBuilder.create().addParam(DATE_PROPERTY, date.format(ISO_DATE)).addParam(CONTINUE_CHOOSE).build())
                .build()
        ));

        var currentDate = date.withDayOfMonth(1);
        var currentMonth = currentDate.getMonthValue();

        for (int weekIndex = 1; weekIndex <= 6; weekIndex++) {
            if (currentDate.getMonthValue() != currentMonth) {
                break;
            }
            List<InlineKeyboardButton> weekRow = new ArrayList<>();
            for (int dayIndex = 1; dayIndex <= 7; dayIndex++) {
                if (currentDate.getDayOfWeek().getValue() > dayIndex || currentDate.getMonthValue() != currentMonth) {
                    weekRow.add(
                        InlineKeyboardButton.builder()
                            .text(" ")
                            .callbackData(CommandBuilder.create()
                                .addParam(DATE_PROPERTY, date.format(ISO_DATE))
                                .addParam(CONTINUE_CHOOSE)
                                .build())
                            .build()
                    );
                    continue;
                }
                var dayOfMonth = currentDate.getDayOfMonth();
                weekRow.add(
                    InlineKeyboardButton.builder()
                        .text(String.valueOf(dayOfMonth))
                        .callbackData(CommandBuilder.create().addAnswer(currentDate.format(ISO_DATE)).build())
                        .build()
                );
                currentDate = currentDate.plusDays(1);
            }
            rowsInline.add(weekRow);
        }

        rowsInline.add(Arrays.asList(
            InlineKeyboardButton.builder()
                .text("<")
                .callbackData(
                    CommandBuilder.create()
                        .addParam(DATE_PROPERTY, date.minusMonths(1).format(ISO_DATE))
                        .addParam(CONTINUE_CHOOSE)
                        .build()
                )
                .build(),
            InlineKeyboardButton.builder()
                .text(">")
                .callbackData(
                    CommandBuilder.create()
                        .addParam(DATE_PROPERTY, date.plusMonths(1).format(ISO_DATE))
                        .addParam(CONTINUE_CHOOSE)
                        .build()
                )
                .build()
        ));

        return InlineKeyboardMarkup.builder()
            .keyboard(rowsInline)
            .build();
    }
}
