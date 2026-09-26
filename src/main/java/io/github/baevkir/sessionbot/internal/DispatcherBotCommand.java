package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.guard.CommandGuard;
import io.github.baevkir.sessionbot.i18n.BotLabels;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;


/**
 * Adapts a {@code @BotCommand} bean to {@link RegisteredCommand}. Runs one dispatch step,
 * suspending the context in {@code progress} when more input is needed, and on completion
 * deletes the chat's question and answer messages to keep the conversation clean.
 */
@Slf4j
public class DispatcherBotCommand implements RegisteredCommand {

    private final CommandsDispatcher commandsDispatcher;
    private final ApplicationContext applicationContext;
    private final List<CommandGuard> guards;

    public DispatcherBotCommand(Object handler, ApplicationContext applicationContext) {
        this.commandsDispatcher = new CommandsDispatcher(handler, applicationContext);
        this.applicationContext = applicationContext;
        this.guards = GuardResolver.guardTypes(AopUtils.getTargetClass(handler)).stream()
            .map(this::resolveGuard)
            .toList();
    }

    public Publisher<? extends PartialBotApiMethod<?>> process(ConversationState commandContext) {
        Assert.isTrue(!ContextState.close.equals(commandContext.getState()), "Cannot process closed context");
        log.debug("Processing command '{}' (state={})", commandsDispatcher.getCommandId(), commandContext.getState());
        var invocationResult = commandsDispatcher.invoke(commandContext);
        if (invocationResult.hasErrors()) {
            return Mono.error(invocationResult.getInvocationError());
        }
        var pendingArguments = commandContext.getPendingArguments();
        if (pendingArguments.isEmpty() && commandContext.getDynamicParams().canSkipAnswer(0)) {
            commandContext.addAnswer("");
        } else {
            pendingArguments.forEach(commandContext::addAnswer);
        }
        if (invocationResult.getInvocationArgument() != null) {
            commandContext.startProgress();
            log.debug("Command '{}' needs more input, prompting user", commandsDispatcher.getCommandId());
            return invocationResult.getInvocationArgument();
        }
        commandContext.close();
        log.debug("Command '{}' complete, cleaning up question/answer messages", commandsDispatcher.getCommandId());
        var removeOldMessages = Flux.<Integer>create(sink -> {
                commandContext.getQuestionMessages().stream()
                    .map(Message::getMessageId)
                    .forEach(sink::next);

                commandContext.getUpdates().forEach(update -> {
                    update.getMessageId().ifPresent(sink::next);
                    update.getCallbackMessage().map(MaybeInaccessibleMessage::getMessageId).ifPresent(sink::next);
                });
                sink.complete();
            })
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

    @Override
    public String getCommandIdentifier() {
        return commandsDispatcher.getCommandId();
    }

    @Override
    public String getDescription(String userName) {
        return applicationContext.getBean(BotLabels.class)
            .resolve(commandsDispatcher.getCommandDescription(), userName);
    }

    @Override
    public boolean hidden() {
        return commandsDispatcher.isHidden();
    }

    @Override
    public List<CommandGuard> guards() {
        return guards;
    }

    private CommandGuard resolveGuard(Class<? extends CommandGuard> type) {
        try {
            return applicationContext.getBean(type);
        } catch (NoUniqueBeanDefinitionException ex) {
            throw new IllegalStateException("Command '" + commandsDispatcher.getCommandId() + "' is @Guarded by "
                + type.getName() + ", but several beans of that type exist", ex);
        } catch (NoSuchBeanDefinitionException ex) {
            throw new IllegalStateException("Command '" + commandsDispatcher.getCommandId() + "' is @Guarded by "
                + type.getName() + ", but no bean of that type exists", ex);
        }
    }
}
