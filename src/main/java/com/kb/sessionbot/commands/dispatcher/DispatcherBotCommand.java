package com.kb.sessionbot.commands.dispatcher;

import com.kb.sessionbot.commands.model.CommandRequest;
import com.kb.sessionbot.commands.IBotCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;

@Slf4j
public class DispatcherBotCommand implements IBotCommand {

    private final CommandsDescriptor commandsDescriptor;

    public DispatcherBotCommand(Object handler, ApplicationContext applicationContext) {
        this.commandsDescriptor = new CommandsDescriptor(handler, applicationContext);
    }

    public Mono<? extends PartialBotApiMethod<?>> process(CommandRequest commandRequest) {
        var invocationResult = commandsDescriptor.invoke(commandRequest);
        Message message = commandRequest.getUpdate().getMessage();
        commandRequest.getContext().addAnswer(message, commandRequest.getPendingArgument());
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

    @Override
    public boolean hidden() {
        return commandsDescriptor.isHidden();
    }
}
