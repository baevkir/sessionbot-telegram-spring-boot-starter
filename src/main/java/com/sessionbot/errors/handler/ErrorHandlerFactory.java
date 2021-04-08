package com.sessionbot.errors.handler;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.exception.ExceptionUtils.getThrowableList;

@Slf4j
public class ErrorHandlerFactory {
    private final List<ErrorHandler<?>> errorHandlers;
    private final Map<Class<Throwable>, ErrorHandler<Throwable>> errorHandlerMap = new HashMap<>();

    public ErrorHandlerFactory(List<ErrorHandler<?>> errorHandlers) {
        this.errorHandlers = errorHandlers;
    }

    @SuppressWarnings("unchecked")
    public Mono<? extends BotApiMethod<?>> handle(Throwable exception) {
        for (Throwable currentError : Lists.reverse(getThrowableList(exception))) {
            ErrorHandler<Throwable> errorHandler = errorHandlerMap.get(currentError.getClass());
            if (errorHandler != null) {
                return errorHandler.handle(currentError);
            }
        }
        log.error("Error during chat bot command", exception);
        return Mono.empty();
    }

    @PostConstruct
    @SuppressWarnings("unchecked")
    public void init() {
        errorHandlers.forEach(errorHandler -> {
            Class type = ((Class) ((ParameterizedType) errorHandler.getClass().getGenericInterfaces()[0]).getActualTypeArguments()[0]);
            errorHandlerMap.put(type, (ErrorHandler<Throwable>) errorHandler);
        });
    }
}
