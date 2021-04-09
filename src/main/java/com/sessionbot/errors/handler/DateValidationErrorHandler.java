package com.sessionbot.errors.handler;

import com.sessionbot.errors.exception.validation.DateValidationException;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class DateValidationErrorHandler implements ErrorHandler<DateValidationException> {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_DATE;

    @Override
    public Mono<? extends BotApiMethod<?>> handle(DateValidationException exception) {
        Long chatId = exception.getErrorData().getCommandRequest().getCommandMessage().getChatId();
        return Mono.fromSupplier(() -> {

            InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
            List<InlineKeyboardButton> rowInline = new ArrayList<>();
            LocalDate currentDate = LocalDate.now();

            rowInline.add(InlineKeyboardButton.builder()
                    .text("Сегодня")
                    .callbackData(currentDate.format(DATE_FORMAT))
                    .build());
            rowInline.add(InlineKeyboardButton.builder()
                    .text("Вчера")
                    .callbackData(currentDate.minusDays(1).format(DATE_FORMAT))
                    .build());
            rowInline.add(InlineKeyboardButton.builder()
                    .text("2 дня назад")
                    .callbackData(currentDate.minusDays(2).format(DATE_FORMAT))
                    .build());
            rowInline.add(InlineKeyboardButton.builder()
                    .text("3 дня назад")
                    .callbackData(currentDate.minusDays(3).format(DATE_FORMAT))
                    .build());


            rowsInline.add(rowInline);
            markupInline.setKeyboard(rowsInline);

            return SendMessage
                    .builder()
                    .chatId(Long.toString(chatId))
                    .text(exception.getMessage() + String.format(" (Формат: %s)", currentDate.format(DATE_FORMAT)))
                    .replyMarkup(markupInline)
                    .build();
        });
    }
}
