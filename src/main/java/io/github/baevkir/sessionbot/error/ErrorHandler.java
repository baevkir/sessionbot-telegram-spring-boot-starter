package io.github.baevkir.sessionbot.error;

import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import reactor.core.publisher.Mono;

public interface ErrorHandler<T extends Throwable> {
    Mono<? extends PartialBotApiMethod<?>> handle(T exception) ;
}
