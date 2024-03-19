package com.kb.sessionbot.commands.dispatcher.parameters;

import com.kb.sessionbot.model.BotCommandResult;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import reactor.core.publisher.Mono;

public interface ParameterRenderer {
    Publisher<BotCommandResult> render(ParameterRequest parameterRequest);
}
