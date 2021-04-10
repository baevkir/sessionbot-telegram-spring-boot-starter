package com.sessionbot.commands.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.Iterables;
import com.sessionbot.commands.CommandRequest;
import com.sessionbot.commands.dispatcher.annotations.BotCommand;
import com.sessionbot.commands.dispatcher.annotations.CommandMethod;
import com.sessionbot.commands.dispatcher.annotations.Parameter;
import com.sessionbot.commands.errors.ErrorData;
import com.sessionbot.commands.errors.exception.BotCommandException;
import com.sessionbot.commands.errors.exception.validation.ChatValidationException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ReflectionUtils;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import reactor.core.publisher.Mono;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class CommandsDescriptor {
    private final Object command;
    private final Map<String, Method> invokerMethods;
    private final ObjectMapper mapper;

    public CommandsDescriptor(Object command) {
        this.command = command;
        this.invokerMethods = parseInvokerMethods(command);
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
    }

    public String getCommandId() {
        return command.getClass().getAnnotation(BotCommand.class).value();
    }

    public String getCommandDescription() {
        return command.getClass().getAnnotation(BotCommand.class).description();
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
                })
                .flatMap(result -> InvocationResultResolver.of(result).resolve())
                .onErrorMap(error -> new BotCommandException(commandRequest, error));
            }
        } catch (Throwable error) {
            invocationResult.invocationError = new BotCommandException(commandRequest, error);
        }

        return invocationResult;
    }

    private Object getMethodArgument(java.lang.reflect.Parameter parameter, CommandRequest commandRequest, InvocationResult invocationResult) {
        if (invocationResult.hasErrors()) {
            return null;
        }
        if (Message.class.equals(parameter.getType()) && parameter.getName().equals("command")) {
            return commandRequest.getCommandMessage();
        } else if (Update.class.equals(parameter.getType()) && parameter.getName().equals("update")) {
            return commandRequest.getUpdate();
        }
        Parameter param = parameter.getAnnotation(Parameter.class);
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

    private ChatValidationException createValidationException(CommandRequest commandRequest, Parameter parameter) {
        String message = String.format("Пожалуйста укажите поле '%s'.", parameter.name());
        try {
            Constructor<? extends ChatValidationException> constructor = parameter.errorType().getConstructor(ErrorData.class);
            return constructor.newInstance(ErrorData.builder()
                    .text(message)
                    .commandRequest(commandRequest)
                    .build());

        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private Map<String, Method> parseInvokerMethods(Object command) {
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
        private Mono<? extends PartialBotApiMethod<?>> invocation;
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
