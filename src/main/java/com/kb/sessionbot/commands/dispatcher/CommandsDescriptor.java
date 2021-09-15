package com.kb.sessionbot.commands.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.kb.sessionbot.commands.dispatcher.annotations.BotCommand;
import com.kb.sessionbot.commands.dispatcher.annotations.CommandMethod;
import com.kb.sessionbot.commands.dispatcher.annotations.Parameter;
import com.kb.sessionbot.commands.dispatcher.parameters.ParameterRenderer;
import com.kb.sessionbot.commands.dispatcher.parameters.ParameterRequest;
import com.kb.sessionbot.errors.exception.BotCommandException;
import com.kb.sessionbot.model.CommandContext;
import com.kb.sessionbot.model.UpdateWrapper;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ReflectionUtils;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.kb.sessionbot.commands.CommandConstants.PARAMETER_SEPARATOR;


@Slf4j
public class CommandsDescriptor {
    private final Object command;
    private final Map<String, Method> invokerMethods;
    private final ObjectMapper mapper;
    private final ApplicationContext applicationContext;

    public CommandsDescriptor(Object command, ApplicationContext applicationContext) {
        this.command = command;
        this.invokerMethods = parseInvokerMethods(command);
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        this.applicationContext = applicationContext;
    }

    public String getCommandId() {
        return command.getClass().getAnnotation(BotCommand.class).value();
    }

    public String getCommandDescription() {
        return command.getClass().getAnnotation(BotCommand.class).description();
    }

    public boolean isHidden() {
        return command.getClass().getAnnotation(BotCommand.class).hidden();
    }

    public InvocationResult invoke(CommandContext context) {
        var invocationResult = new InvocationResult();

        try {
            invocationResult.invocationMethod = findInvokerMethod(context, invocationResult);
            if (invocationResult.invocationMethod == null) {
                if (invocationResult.invocationArgument != null) {
                    return invocationResult;
                }
                throw new RuntimeException(
                    "Cannot find command method for " + context.getCommand() + " with arguments " + context.getAnswers()
                );
            }
            var args = new ArrayList<>();
            for (java.lang.reflect.Parameter parameter : invocationResult.invocationMethod.getParameters()) {
                if (invocationResult.hasErrors()) {
                    continue;
                }
                if (UpdateWrapper.class.equals(parameter.getType()) && parameter.getName().equals("command")) {
                    args.add(context.getCommandUpdate());
                    continue;
                } else if (UpdateWrapper.class.equals(parameter.getType()) && parameter.getName().equals("update")) {
                    args.add(context.getCurrentUpdate().orElse(null));
                    continue;
                } else if (Update.class.equals(parameter.getType()) && parameter.getName().equals("update")) {
                    args.add(context.getCurrentUpdate().map(UpdateWrapper::getUpdate).orElse(null));
                    continue;
                } else if (User.class.equals(parameter.getType()) && parameter.getName().equals("from")) {
                    args.add(context.getCommandUpdate().getFrom());
                    continue;
                }
                if (CommandContext.class.equals(parameter.getType())) {
                    args.add(context);
                    continue;
                }

                Object argument = getArgument(context, parameter);
                if (argument != null) {
                    invocationResult.addArgument(argument);
                    args.add(argument);
                } else {
                    Parameter param = parameter.getAnnotation(Parameter.class);
                    if (!param.required() && context.getCurrentUpdate().map(UpdateWrapper::scipAnswer).orElse(false)) {
                        args.add(null);
                    } else {
                        invocationResult.invocationArgument = getRenderer(param).render(
                            ParameterRequest.builder()
                                .text(String.format("Пожалуйста укажите поле '%s'.", param.name().isEmpty()? parameter.getName() : param.name()))
                                .parameterType(parameter.getType())
                                .required(param.required())
                                .context(context)
                                .options(Sets.newHashSet(param.rendering().options()))
                                .build()
                        );
                        return invocationResult;
                    }

                }
            }

            if (!invocationResult.hasErrors()) {
                invocationResult.invocation = Mono.fromSupplier(
                        () -> {
                            ReflectionUtils.makeAccessible(invocationResult.invocationMethod);
                            return ReflectionUtils.invokeMethod(
                                invocationResult.invocationMethod,
                                command,
                                args.toArray()
                            );
                        })
                    .flatMapMany(result -> InvocationResultResolver.of(result).resolve())
                    .onErrorMap(error -> new BotCommandException(context, error));
            }
        } catch (Throwable error) {
            invocationResult.invocationError = new BotCommandException(context, error);
        }

        return invocationResult;
    }

    private Object getArgument(CommandContext context, java.lang.reflect.Parameter parameter) {
        Parameter param = parameter.getAnnotation(Parameter.class);
        int index = param.index();
        var answers = context.getAnswers();
        if (index < answers.size()) {
            return mapper.convertValue(answers.get(index), parameter.getType());
        }
        return null;
    }

    private ParameterRenderer getDefaultRenderer() {
        return applicationContext.getBean("defaultParameterRenderer", ParameterRenderer.class);
    }

    private ParameterRenderer getRenderer(Parameter parameter) {
        if (parameter.rendering().type() != ParameterRenderer.class) {
            return applicationContext.getBean(parameter.rendering().type());
        }
        return applicationContext.getBean(parameter.rendering().name(), ParameterRenderer.class);
    }

    private Map<String, Method> parseInvokerMethods(Object command) {
        return Arrays.stream(command.getClass().getMethods())
            .filter(method -> method.isAnnotationPresent(CommandMethod.class))
            .peek(method -> log.debug("Find OperationMethod {} for class {}.", method, command.getClass()))
            .collect(Collectors.toMap(method -> method.getAnnotation(CommandMethod.class).arguments(), Function.identity()));
    }

    private Method findInvokerMethod(CommandContext commandContext, InvocationResult invocationResult) {
        if (invokerMethods.keySet().size() == 1) {
            return Iterables.getFirst(invokerMethods.values(), null);
        }
        Method defaultMethod = invokerMethods.get("");
        var arguments = commandContext.getAnswers();

        if (arguments.isEmpty()) {
            if (defaultMethod != null) {
                return defaultMethod;
            }
            invocationResult.invocationArgument = getDefaultRenderer().render(
                ParameterRequest.builder()
                    .context(commandContext)
                    .text(String.format("Пожалуйста выберите опцию для команды '%s'", commandContext.getCommand()))
                    .options(invokerMethods.keySet())
                    .build()
            );
            return null;
        }
        return findInvokerMethodByArguments(arguments).orElseGet(() -> {
            if (defaultMethod != null) {
                return defaultMethod;
            }
            invocationResult.invocationArgument = getDefaultRenderer().render(
                ParameterRequest.builder()
                    .context(commandContext)
                    .text(String.format("Опция не поддерживается для команды %s", commandContext.getCommand()))
                    .options(invokerMethods.keySet())
                    .build()
            );
            return null;
        });
    }

    private Optional<Method> findInvokerMethodByArguments(List<Object> arguments) {
        var argumentsString = arguments.stream().map(String::valueOf).collect(Collectors.joining(PARAMETER_SEPARATOR));
        while (!argumentsString.isEmpty()) {
            Method method = invokerMethods.get(argumentsString);
            if (method != null) {
                return Optional.of(method);
            }
            var lastIndex = argumentsString.contains(PARAMETER_SEPARATOR) ? argumentsString.lastIndexOf(PARAMETER_SEPARATOR) : 0;
            argumentsString = argumentsString.substring(0, lastIndex);
        }
        return Optional.empty();
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class InvocationResult {
        private Publisher<? extends PartialBotApiMethod<?>> invocation;
        private Publisher<? extends PartialBotApiMethod<?>> invocationArgument;
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
