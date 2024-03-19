package com.kb.sessionbot.commands.dispatcher.parameters;

import com.kb.sessionbot.commands.dispatcher.InvocationResultResolver;
import com.kb.sessionbot.errors.exception.BotCommandException;
import com.kb.sessionbot.model.BotCommandResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

import static reactor.core.publisher.Mono.fromSupplier;

@Slf4j
@AllArgsConstructor
public class RendererDispatcher implements ParameterRenderer{
    private final Object handler;
    private final Method method;

    @Override
    public Publisher<BotCommandResult> render(ParameterRequest parameterRequest) {
        return fromSupplier(() -> invokeRenderer(parameterRequest))
            .flatMapMany(result -> InvocationResultResolver.of(result).resolve())
            .onErrorMap(error -> new BotCommandException(parameterRequest.getContext(), error));
    }

    private Object invokeRenderer(ParameterRequest parameterRequest) {
        ReflectionUtils.makeAccessible(method);
        return ReflectionUtils.invokeMethod(method, handler, parameterRequest);
    }
}
