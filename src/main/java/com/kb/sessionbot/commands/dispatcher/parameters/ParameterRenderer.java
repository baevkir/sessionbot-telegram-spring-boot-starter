package com.kb.sessionbot.commands.dispatcher.parameters;

import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import reactor.core.publisher.Mono;

public interface ParameterRenderer {
    Mono<? extends PartialBotApiMethod<?>> render(ParameterRequest parameterRequest);
}
