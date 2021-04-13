package com.kb.sessionbot.commands.errors.handler;

import com.kb.sessionbot.commands.CommandSessionsHolder;
import com.kb.sessionbot.commands.errors.exception.BotAuthException;
import com.kb.sessionbot.commands.errors.exception.BotCommandException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Slf4j
public class BotAuthErrorHandler implements ErrorHandler<BotAuthException> {

    @Override
    public Mono<? extends PartialBotApiMethod<?>> handle(BotAuthException exception) {
        log.error("Authentication failure", exception);
        return Mono.fromSupplier(() ->
                SendMessage
                        .builder()
                        .chatId(exception.getRequest().getChatId())
                        .text(exception.getMessage())
                        .build()
        );
    }
}
