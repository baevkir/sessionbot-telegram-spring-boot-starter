package com.sessionbot.commands;

import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import reactor.core.publisher.Mono;

@Slf4j
public class ReactiveBotCommand implements IBotCommand {
    public final static String COMMAND_INIT_CHARACTER = "/";

    private final CommandsSessionCash commandsSessionCash;
    private final CommandsDescriptor commandsDescriptor;

    public ReactiveBotCommand(Object handler, CommandsSessionCash commandsSessionCash) {
        this.commandsDescriptor = new CommandsDescriptor(handler);
        this.commandsSessionCash = commandsSessionCash;
    }

    public Mono<? extends BotApiMethod<?>> process(CommandRequest commandRequest) {
        var invocationResult = commandsDescriptor.invoke(commandRequest);
        commandsSessionCash.updateSessionArguments(
                commandRequest.getCommandMessage().getFrom().getId(),
                commandRequest.getCommandMessage().getChatId(),
                invocationResult.getCommandArguments()
        );
        if (invocationResult.hasErrors()) {
            return Mono.error(invocationResult.getInvocationError());
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
    public String toString() {
        return "<b>" + COMMAND_INIT_CHARACTER + getCommandIdentifier() +
                "</b>\n" + getDescription();
    }
}
