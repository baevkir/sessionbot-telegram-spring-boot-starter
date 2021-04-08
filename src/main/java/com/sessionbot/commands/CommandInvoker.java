package com.sessionbot.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.Iterables;
import com.sessionbot.annotations.Param;
import com.sessionbot.annotations.CommandMethod;
import com.sessionbot.errors.ErrorData;
import com.sessionbot.errors.exception.BotCommandException;
import com.sessionbot.errors.exception.validation.ChatValidationException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ReflectionUtils;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import reactor.core.publisher.Mono;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class CommandInvoker {
    private final BotCommand command;
    private final Map<String, Method> invokerMethods;
    private final ObjectMapper mapper;

    public CommandInvoker(BotCommand command) {
        this.command = command;
        this.invokerMethods = parseInvokerMethods(command);
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
    }

    @SuppressWarnings("unchecked")
    public InvocationResult invoke(CommandRequest commandRequest) {
        var invocationResult = new InvocationResult();

        try {
            invocationResult.invocationMethod = findInvokerMethod(commandRequest, invocationResult);
            var arguments = Stream.of(invocationResult.invocationMethod.getParameters())
                    .map(parameter -> getMethodArgument(parameter, commandRequest, invocationResult))
                    .toArray();

            if (!invocationResult.hasErrors()) {
                invocationResult.invocation = Mono.fromSupplier(() -> {
                    ReflectionUtils.makeAccessible(invocationResult.invocationMethod);
                    return ReflectionUtils.invokeMethod(
                            invocationResult.invocationMethod,
                            command,
                            arguments
                    );
                }).flatMap(invokeResult -> (Mono<? extends BotApiMethod<?>>) Objects.requireNonNull(invokeResult))
                        .onErrorMap(error -> new BotCommandException(commandRequest, error));
            }
        } catch (Throwable error) {
            invocationResult.invocationError = new BotCommandException(commandRequest, error);
        }

        return invocationResult;
    }

    private Object getMethodArgument(Parameter parameter, CommandRequest commandRequest, InvocationResult invocationResult) {
        if (invocationResult.hasErrors()) {
            return null;
        }
        if (Message.class.equals(parameter.getType()) && parameter.getName().equals("command")) {
            return commandRequest.getCommandMessage();
        } else if (Update.class.equals(parameter.getType()) && parameter.getName().equals("update")) {
            return commandRequest.getUpdate();
        }
        Param param = parameter.getAnnotation(Param.class);
        int index = param.index();
        if (index < commandRequest.getArguments().size()) {
            Object argument = mapper.convertValue(commandRequest.getArguments().get(index), parameter.getType());
            invocationResult.addArgument(argument);
            return argument;
        }
        if (index == commandRequest.getArguments().size() && commandRequest.getPendingArgument() != null) {
            Object argument = mapper.convertValue(commandRequest.getPendingArgument(), parameter.getType());
            invocationResult.addArgument(argument);
            return argument;
        }
        throw createValidationException(commandRequest, param);
    }

    private ChatValidationException createValidationException(CommandRequest commandRequest, Param param) {
        String message = String.format("Пожалуйста укажите поле '%s'.", param.displayName());
        try {
            Constructor<? extends ChatValidationException> constructor = param.errorType().getConstructor(ErrorData.class);
            return constructor.newInstance(ErrorData.builder()
                    .text(message)
                    .commandRequest(commandRequest)
                    .build());

        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private Map<String, Method> parseInvokerMethods(BotCommand command) {
        return Arrays.stream(command.getClass().getMethods())
                .filter(method -> method.isAnnotationPresent(CommandMethod.class))
                .peek(method -> log.debug("Find OperationMethod {} for class {}.", method, command.getClass()))
                .collect(Collectors.toMap(method -> method.getAnnotation(CommandMethod.class).argument(), Function.identity()));
    }

    private Method findInvokerMethod(CommandRequest commandRequest, InvocationResult invocationResult) {
        Object argument = Iterables.getFirst(commandRequest.getArguments(), commandRequest.getPendingArgument());
        Method defaultMethod = invokerMethods.get("");

        if (argument == null) {
            if (defaultMethod != null) {
                return defaultMethod;
            }
            throw new ChatValidationException(ErrorData.builder()
                    .commandRequest(commandRequest)
                    .text(String.format("Пожалуйста выберите опцию для команды '%s'", commandRequest.getCommand()))
                    .options(invokerMethods.keySet())
                    .build());
        }
        Method method = invokerMethods.get(argument.toString());
        if (method == null) {
            if (defaultMethod != null) {
                return defaultMethod;
            }
            throw new ChatValidationException(ErrorData.builder()
                    .commandRequest(commandRequest)
                    .text(String.format("Опция '%s' не поддерживается для команды %s", argument, commandRequest.getCommand()))
                    .options(invokerMethods.keySet())
                    .build());
        }
        invocationResult.addArgument(argument);
        return method;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class InvocationResult {
        private Mono<? extends BotApiMethod<?>> invocation;
        private Method invocationMethod;
        private final List<Object> commandArguments = new ArrayList<>();
        private Throwable invocationError;

        public boolean hasErrors() {
            return invocationError != null;
        }

        private void addArgument(Object argument) {
            commandArguments.add(argument);
        }
    }

}
