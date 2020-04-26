package com.sessionbot.bot.errors.handler;

import com.sessionbot.bot.CommandsSessionCash;
import com.sessionbot.bot.errors.exception.BotCommandException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Slf4j
@Component
public class BotCommandErrorHandler implements ErrorHandler<BotCommandException>{
    private CommandsSessionCash commandsSessionCash;

    public BotCommandErrorHandler(CommandsSessionCash commandsSessionCash) {
        this.commandsSessionCash = commandsSessionCash;
    }

    @Override
    public Mono<? extends BotApiMethod<?>> handle(BotCommandException exception) {
        String botMessage = Optional.ofNullable(ExceptionUtils.getRootCause(exception).getMessage())
                .orElse("Error during chat bot command. Please try again letter.");
        Long chatId = exception.getCommandRequest().getCommandMessage().getChatId();
        log.error(botMessage, exception);
        return Mono.fromSupplier(() -> {
            commandsSessionCash.closeSession(exception.getCommandRequest().getCommandMessage());

            return new SendMessage()
                    .setChatId(chatId)
                    .setText(botMessage);
        });
    }
}
