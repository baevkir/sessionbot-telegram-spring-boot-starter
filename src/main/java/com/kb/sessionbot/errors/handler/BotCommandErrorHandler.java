package com.kb.sessionbot.errors.handler;

import com.kb.sessionbot.errors.exception.BotCommandException;
import com.kb.sessionbot.i18n.BotLabels;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import reactor.core.publisher.Mono;

/**
 * Default handler for a failed command: logs the error and tells the user something went wrong,
 * without the exception's own message, which may expose internals. To show a user a specific text,
 * register an {@link ErrorHandler} for your own exception type.
 */
@Slf4j
public class BotCommandErrorHandler implements ErrorHandler<BotCommandException> {

    private final BotLabels labels;

    public BotCommandErrorHandler(BotLabels labels) {
        this.labels = labels;
    }

    @Override
    public Mono<? extends PartialBotApiMethod<?>> handle(BotCommandException exception) {
        log.error("Command failed in chat {}", exception.getContext().getChatId(), exception);
        return Mono.fromSupplier(() ->
                SendMessage
                        .builder()
                        .chatId(exception.getContext().getChatId())
                        .text(labels.errorGeneric(exception.getContext()))
                        .build()
        );
    }
}
