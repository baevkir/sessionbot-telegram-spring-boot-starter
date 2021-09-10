package com.kb.sessionbot.errors.handler;

import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import reactor.core.publisher.Mono;

public interface ErrorHandler<T extends Throwable> {
    Mono<? extends PartialBotApiMethod<?>> handle(T exception) ;
}
