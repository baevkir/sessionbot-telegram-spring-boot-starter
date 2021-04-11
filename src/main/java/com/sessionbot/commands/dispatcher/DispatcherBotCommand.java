package com.sessionbot.commands.dispatcher;

import com.sessionbot.commands.CommandRequest;
import com.sessionbot.commands.CommandSessionsHolder;
import com.sessionbot.commands.IBotCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import reactor.core.publisher.Mono;

@Slf4j
public class DispatcherBotCommand implements IBotCommand {

    private final CommandSessionsHolder commandSessionsHolder;
    private final CommandsDescriptor commandsDescriptor;

    public DispatcherBotCommand(Object handler, CommandSessionsHolder commandSessionsHolder, ApplicationContext applicationContext) {
        this.commandsDescriptor = new CommandsDescriptor(handler, applicationContext);
        this.commandSessionsHolder = commandSessionsHolder;
    }

    public Mono<? extends PartialBotApiMethod<?>> process(CommandRequest commandRequest) {
        var invocationResult = commandsDescriptor.invoke(commandRequest);
        commandSessionsHolder.updateSessionArguments(
                commandRequest.getCommandMessage().getFrom().getId(),
                commandRequest.getCommandMessage().getChatId(),
                invocationResult.getCommandArguments()
        );
        if (invocationResult.hasErrors()) {
            return Mono.error(invocationResult.getInvocationError());
        }
        if (invocationResult.getInvocationArgument() != null) {
            return invocationResult.getInvocationArgument();
        }
        return invocationResult.getInvocation();
    }

    @Override
    public String getCommandIdentifier() {
        return commandsDescriptor.getCommandId();
    }

    @Override
    public String getDescription() {
        return commandsDescriptor.getCommandDescription();
    }
}
