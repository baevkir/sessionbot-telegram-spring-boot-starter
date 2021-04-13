package com.kb.sessionbot.commands.errors.handler;

import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import reactor.core.publisher.Mono;

public interface ErrorHandler<T extends Throwable> {
    Mono<? extends PartialBotApiMethod<?>> handle(T exception) ;
}
