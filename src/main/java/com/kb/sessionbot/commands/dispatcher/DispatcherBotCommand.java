package com.kb.sessionbot.commands.dispatcher;

import com.kb.sessionbot.commands.IBotCommand;
import com.kb.sessionbot.model.CommandContext;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.context.ApplicationContext;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class DispatcherBotCommand implements IBotCommand {

    private final CommandsDispatcher commandsDispatcher;

    public DispatcherBotCommand(Object handler, ApplicationContext applicationContext) {
        this.commandsDispatcher = new CommandsDispatcher(handler, applicationContext);
    }

    public Publisher<? extends PartialBotApiMethod<?>> process(CommandContext commandContext) {
        var invocationResult = commandsDispatcher.invoke(commandContext);
        if (invocationResult.hasErrors()) {
            return Mono.error(invocationResult.getInvocationError());
        }
        commandContext.getPendingArguments().forEach(commandContext::addAnswer);
        if (invocationResult.getInvocationArgument() != null) {
            return invocationResult.getInvocationArgument();
        }
        if (commandContext.getUpdates().size() >= 2) {
            var removeOldMessages = Flux.fromIterable(commandContext.getUpdates())
                .skip(1)
                .mapNotNull(update -> update.getCallbackMessage().orElse(null))
                .map(Message::getMessageId)
                .distinct()
                .map(messageId ->
                    DeleteMessage.builder()
                        .chatId(commandContext.getChatId())
                        .messageId(messageId)
                        .build()
                );
            return Flux.concat(
                invocationResult.getInvocation(),
                removeOldMessages
            );
        }
        return invocationResult.getInvocation();
    }

    @Override
    public String getCommandIdentifier() {
        return commandsDispatcher.getCommandId();
    }

    @Override
    public String getDescription() {
        return commandsDispatcher.getCommandDescription();
    }

    @Override
    public boolean hidden() {
        return commandsDispatcher.isHidden();
    }
}
