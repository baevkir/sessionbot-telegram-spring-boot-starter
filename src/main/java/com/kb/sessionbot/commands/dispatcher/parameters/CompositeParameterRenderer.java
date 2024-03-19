package com.kb.sessionbot.commands.dispatcher.parameters;

import com.kb.sessionbot.model.BotCommandResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;

import java.time.LocalDate;

@Slf4j
@AllArgsConstructor
public class CompositeParameterRenderer implements ParameterRenderer{
    private final ParameterRenderer textParameterRenderer;
    private final ParameterRenderer dateParameterRenderer;
    private final ParameterRenderer booleanParameterRenderer;

    @Override
    public Publisher<BotCommandResult> render(ParameterRequest parameterRequest) {
        if (LocalDate.class == parameterRequest.getParameterType()){
            return dateParameterRenderer.render(parameterRequest);
        }
        if (Boolean.class == parameterRequest.getParameterType() || Boolean.TYPE == parameterRequest.getParameterType()) {
            return booleanParameterRenderer.render(parameterRequest);
        }
        return textParameterRenderer.render(parameterRequest);
    }
}
