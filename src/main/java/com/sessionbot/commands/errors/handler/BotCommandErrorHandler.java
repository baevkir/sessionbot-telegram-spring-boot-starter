package com.sessionbot.commands.errors.handler;

import com.sessionbot.commands.errors.exception.BotCommandException;
import com.sessionbot.commands.CommandSessionsHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Slf4j
public class BotCommandErrorHandler implements ErrorHandler<BotCommandException>{
    private final CommandSessionsHolder commandSessionsHolder;

    public BotCommandErrorHandler(CommandSessionsHolder commandSessionsHolder) {
        this.commandSessionsHolder = commandSessionsHolder;
    }

    @Override
    public Mono<? extends BotApiMethod<?>> handle(BotCommandException exception) {
        String botMessage = Optional.ofNullable(ExceptionUtils.getRootCause(exception).getMessage())
                .orElse("Error during chat bot command. Please try again letter.");
        Long chatId = exception.getCommandRequest().getCommandMessage().getChatId();
        log.error(botMessage, exception);
        return Mono.fromSupplier(() -> {
            commandSessionsHolder.closeSession(exception.getCommandRequest().getCommandMessage());

            return SendMessage
                    .builder()
                    .chatId(Long.toString(chatId))
                    .text(botMessage)
                    .build();
        });
    }
}
