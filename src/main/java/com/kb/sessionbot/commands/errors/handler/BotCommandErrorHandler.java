package com.kb.sessionbot.commands.errors.handler;

import com.kb.sessionbot.commands.errors.exception.BotCommandException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Slf4j
public class BotCommandErrorHandler implements ErrorHandler<BotCommandException> {

    @Override
    public Mono<? extends PartialBotApiMethod<?>> handle(BotCommandException exception) {
        String botMessage = Optional.ofNullable(ExceptionUtils.getRootCause(exception).getMessage())
                .orElse("Error during chat bot command. Please try again letter.");
        Long chatId = exception.getCommandRequest().getContext().getCommandMessage().getChatId();
        log.error(botMessage, exception);
        return Mono.fromSupplier(() ->
                SendMessage
                        .builder()
                        .chatId(Long.toString(chatId))
                        .text(botMessage)
                        .build()
        );
    }
}
