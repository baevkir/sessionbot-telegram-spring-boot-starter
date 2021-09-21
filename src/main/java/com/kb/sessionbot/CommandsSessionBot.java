package com.kb.sessionbot;

import com.kb.sessionbot.auth.AuthInterceptor;
import com.kb.sessionbot.commands.CommandsFactory;
import com.kb.sessionbot.config.CommandsSessionBotProperties;
import com.kb.sessionbot.errors.exception.BotAuthException;
import com.kb.sessionbot.errors.handler.ErrorHandlerFactory;
import com.kb.sessionbot.model.CommandContext;
import com.kb.sessionbot.model.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

import javax.annotation.PostConstruct;

@Slf4j
public class CommandsSessionBot extends TelegramLongPollingBot {

    private final CommandsFactory commandsFactory;
    private final ErrorHandlerFactory errorHandler;
    private final AuthInterceptor authInterceptor;
    private final CommandsSessionBotProperties properties;
    private final Sinks.Many<Update> updatesSink = Sinks.many().unicast().onBackpressureBuffer();
    private final Sinks.Many<PartialBotApiMethod<?>> messagesSink = Sinks.many().unicast().onBackpressureBuffer();

    public CommandsSessionBot(
        CommandsFactory commandsFactory,
        AuthInterceptor authInterceptor,
        ErrorHandlerFactory errorHandler,
        CommandsSessionBotProperties properties
    ) {
        this.commandsFactory = commandsFactory;
        this.errorHandler = errorHandler;
        this.authInterceptor = authInterceptor;
        this.properties = properties;
    }


    public void sendMessage(PartialBotApiMethod<?> message) {
        messagesSink.tryEmitNext(message);
    }

    @Override
    public void onUpdateReceived(Update update) {
        updatesSink.tryEmitNext(update);
    }

    @Override
    public String getBotToken() {
        return properties.getToken();
    }

    @Override
    public String getBotUsername() {
        return properties.getBotUsername();
    }

    @PostConstruct
    public void init() {
        var setMyCommands = Flux.fromIterable(commandsFactory.getCommands())
            .filter(command -> !command.hidden())
            .map(command -> BotCommand.builder().command(command.getCommandIdentifier()).description(command.getDescription()).build())
            .collectList()
            .map(commands -> SetMyCommands.builder().commands(commands).build());

        Flux.concat(
                setMyCommands,
                updatesSink.asFlux()
                    .map(UpdateWrapper::wrap)
                    .groupBy(UpdateWrapper::getChatId)
                    .flatMap(updates -> this.handleUpdates(updates).onErrorResume(errorHandler::handle))
                    .mergeWith(messagesSink.asFlux())
                    .retry()
            ).subscribe(this::executeMessage);
    }

    private Flux<PartialBotApiMethod<?>> handleUpdates(Flux<UpdateWrapper> updates) {
        Assert.notNull(updates, "Updates is null.");
        return updates
            .scanWith(CommandContext::empty, (context, update) -> {
                if (update.isCommand()) {
                    return CommandContext.create(update);
                }
                if (update.getDynamicParams().needRefreshContext() && !context.isEmpty()) {
                    return CommandContext.create(context.getCommandUpdate()).addUpdate(update);
                }
                return context.addUpdate(update);
            })
            .skip(1)
            .flatMap(context -> {
                if (context.isEmpty()) {
                    return commandsFactory.getHelpCommand().process(context);
                }
                if (!authInterceptor.intercept(context)) {
                    return Flux.error(new BotAuthException(context, "User is unauthorized to use bot."));
                }
                return commandsFactory.getCommand(context.getCommand()).process(context);
            });
    }

    private void executeMessage(PartialBotApiMethod<?> message) {
        try {
            if (message instanceof BotApiMethod) {
                execute((BotApiMethod<?>) message);
            } else {
                throw new UnsupportedOperationException("Message type " + message.getClass().getSimpleName() + " is not supported yet");
            }
        } catch (TelegramApiException e) {
            log.error("Cannot execute message", e);
        }
    }
}
