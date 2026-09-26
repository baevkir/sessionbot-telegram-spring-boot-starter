package io.github.baevkir.sessionbot.render;

import io.github.baevkir.sessionbot.CommandBuilder;
import io.github.baevkir.sessionbot.i18n.BotLabels;
import io.github.baevkir.sessionbot.UpdateWrapper;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a {@code LocalTime} parameter as buttons: an hour grid first, then that hour's quarter hours.
 * Moving between the two steps rides dynamic parameters and edits the same message, the way
 * {@link DateParameterRenderer} pages months; a minute button answers {@code HH:mm}.
 */
public class TimeParameterRenderer implements ParameterRenderer {

    private static final String HOUR_PROPERTY = "time-renderer-hour";
    private static final String CONTINUE_CHOOSE = "time-renderer-continue";
    private static final int HOURS_PER_ROW = 6;
    private static final List<Integer> MINUTES = List.of(0, 15, 30, 45);

    private final BotLabels labels;

    public TimeParameterRenderer(BotLabels labels) {
        this.labels = labels;
    }

    @Override
    public Publisher<? extends PartialBotApiMethod<?>> render(ParameterRequest parameterRequest) {
        return Mono.fromSupplier(() -> {
            var context = parameterRequest.getContext();
            var dynamicParams = context.getDynamicParams();
            var keyboard = dynamicParams.hasParam(HOUR_PROPERTY)
                ? minuteKeyboard(parameterRequest, Integer.parseInt(dynamicParams.getParam(HOUR_PROPERTY)))
                : hourKeyboard(parameterRequest);
            var callbackMessage = context.getCurrentUpdate().flatMap(UpdateWrapper::getCallbackMessage);
            if (dynamicParams.hasParam(CONTINUE_CHOOSE) && callbackMessage.isPresent()) {
                return EditMessageReplyMarkup.builder()
                    .chatId(context.getChatId())
                    .messageId(callbackMessage.get().getMessageId())
                    .replyMarkup(keyboard)
                    .build();
            }
            return SendMessage.builder()
                .chatId(context.getChatId())
                .text(parameterRequest.getText())
                .replyMarkup(keyboard)
                .build();
        });
    }

    private InlineKeyboardMarkup hourKeyboard(ParameterRequest parameterRequest) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        var row = new InlineKeyboardRow();
        for (int hour = 0; hour < 24; hour++) {
            row.add(InlineKeyboardButton.builder()
                .text(String.format("%02d", hour))
                .callbackData(CommandBuilder.create()
                    .addParam(HOUR_PROPERTY, String.valueOf(hour))
                    .addParam(CONTINUE_CHOOSE)
                    .build())
                .build());
            if (row.size() == HOURS_PER_ROW) {
                rows.add(row);
                row = new InlineKeyboardRow();
            }
        }
        addSkipRow(rows, parameterRequest);
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardMarkup minuteKeyboard(ParameterRequest parameterRequest, int hour) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        var minuteRow = new InlineKeyboardRow();
        for (int minute : MINUTES) {
            var time = String.format("%02d:%02d", hour, minute);
            minuteRow.add(InlineKeyboardButton.builder()
                .text(time)
                .callbackData(CommandBuilder.create().addAnswer(time).build())
                .build());
        }
        rows.add(minuteRow);
        rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
            .text(labels.back(parameterRequest.getContext()))
            .callbackData(CommandBuilder.create().addParam(CONTINUE_CHOOSE).build())
            .build()));
        addSkipRow(rows, parameterRequest);
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private void addSkipRow(List<InlineKeyboardRow> rows, ParameterRequest parameterRequest) {
        if (!parameterRequest.isRequired()) {
            rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                .text(labels.skip(parameterRequest.getContext()))
                .callbackData(CommandBuilder.create().scipAnswer(parameterRequest.getIndex()).build())
                .build()));
        }
    }
}
