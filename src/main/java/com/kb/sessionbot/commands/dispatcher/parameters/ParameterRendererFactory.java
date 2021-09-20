package com.kb.sessionbot.commands.dispatcher.parameters;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;

import java.time.LocalDate;

@Slf4j
@AllArgsConstructor
public class ParameterRendererFactory implements ParameterRenderer{
    private final ParameterRenderer textParameterRenderer;
    private final ParameterRenderer dateParameterRenderer;
    private final ParameterRenderer booleanParameterRenderer;

    @Override
    public Publisher<? extends PartialBotApiMethod<?>> render(ParameterRequest parameterRequest) {
        if (LocalDate.class.isAssignableFrom(parameterRequest.getParameterType())){
            return dateParameterRenderer.render(parameterRequest);
        }
        if (Boolean.class.isAssignableFrom(parameterRequest.getParameterType())) {
            return booleanParameterRenderer.render(parameterRequest);
        }
        return textParameterRenderer.render(parameterRequest);
    }
}
