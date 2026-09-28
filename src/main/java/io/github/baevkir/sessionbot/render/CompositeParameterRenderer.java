package io.github.baevkir.sessionbot.render;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;

import java.time.LocalDate;
import java.time.LocalTime;

@Slf4j
@AllArgsConstructor
public class CompositeParameterRenderer implements ParameterRenderer{
    private final ParameterRenderer textParameterRenderer;
    private final ParameterRenderer dateParameterRenderer;
    private final ParameterRenderer booleanParameterRenderer;
    private final ParameterRenderer timeParameterRenderer;

    @Override
    public Publisher<? extends PartialBotApiMethod<?>> render(ParameterRequest parameterRequest) {
        if (LocalTime.class == parameterRequest.getParameterType()) {
            return timeParameterRenderer.render(parameterRequest);
        }
        if (LocalDate.class == parameterRequest.getParameterType()){
            return dateParameterRenderer.render(parameterRequest);
        }
        if (Boolean.class == parameterRequest.getParameterType() || Boolean.TYPE == parameterRequest.getParameterType()) {
            return booleanParameterRenderer.render(parameterRequest);
        }
        return textParameterRenderer.render(parameterRequest);
    }
}
