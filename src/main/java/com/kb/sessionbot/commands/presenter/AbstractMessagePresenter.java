package com.kb.sessionbot.commands.presenter;

import com.kb.sessionbot.model.CommandContext;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import reactor.core.publisher.Flux;

import java.util.List;

public abstract class AbstractMessagePresenter<S> implements  BotMethodPresenter<S> {
    @Override
    public Publisher<BotApiMethod<?>> buildMessage(S source, CommandContext context) {
        return Flux.create(sink -> {
            var builder = SendMessage.builder()
                .chatId(context.getChatId())
                .text(buildText(source, context))
                .parseMode(parseMode(source, context));
            var keyboard = buildKeyboard(source, context);
            if (keyboard != null) {
                builder.replyMarkup(InlineKeyboardMarkup.builder().keyboard(buildKeyboard(source, context)).build());
            }
            sink.next(builder.build());
            sink.complete();
        });
    }

    @Override
    public Publisher<BotApiMethod<?>> buildEditMessage(S source, CommandContext context, Integer messageId) {
        return Flux.create(sink -> {
            var text = buildText(source, context);
            var keyboard = buildKeyboard(source, context);
            if (text != null) {
                var builder = EditMessageText.builder()
                    .chatId(context.getChatId())
                    .messageId(messageId)
                    .parseMode(parseMode(source, context))
                    .text(text);

                if (keyboard != null) {
                    builder.replyMarkup(InlineKeyboardMarkup.builder().keyboard(keyboard).build());
                }
                sink.next(builder.build());
            } else {
                if (keyboard != null) {
                    sink.next(
                        EditMessageReplyMarkup.builder()
                            .chatId(context.getChatId())
                            .messageId(messageId)
                            .replyMarkup(InlineKeyboardMarkup.builder().keyboard(keyboard).build())
                            .build()
                    );
                }
            }
            sink.complete();
        });
    }

    protected abstract String buildText(S source, CommandContext context);

    protected List<List<InlineKeyboardButton>> buildKeyboard(S source, CommandContext context) {
        return null;
    }

    protected String parseMode(S source, CommandContext context) {
        return ParseMode.HTML;
    }
}
