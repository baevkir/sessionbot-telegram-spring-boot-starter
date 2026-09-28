package io.github.baevkir.sessionbot.error;

import io.github.baevkir.sessionbot.i18n.BotLabels;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import reactor.core.publisher.Mono;

@Slf4j
public class BotAuthErrorHandler implements ErrorHandler<BotAuthException> {

    private final BotLabels labels;

    public BotAuthErrorHandler(BotLabels labels) {
        this.labels = labels;
    }

    @Override
    public Mono<? extends PartialBotApiMethod<?>> handle(BotAuthException exception) {
        log.warn("Authentication rejected in chat {}: {}", exception.getContext().getChatId(), exception.getMessage());
        return Mono.fromSupplier(() ->
                SendMessage
                        .builder()
                        .chatId(exception.getContext().getChatId())
                        .text(labels.unauthorized(exception.getContext()))
                        .build()
        );
    }
}
