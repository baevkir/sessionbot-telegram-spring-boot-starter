package com.kb.sessionbot.commands;

import com.kb.sessionbot.commands.auth.AuthInterceptor;
import com.kb.sessionbot.commands.errors.exception.BotAuthException;
import com.kb.sessionbot.commands.errors.handler.ErrorHandlerFactory;
import com.kb.sessionbot.commands.model.CommandContext;
import com.kb.sessionbot.commands.model.CommandRequest;
import com.kb.sessionbot.commands.model.UpdateWrapper;
import com.kb.sessionbot.configurer.CommandsSessionBotProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import javax.annotation.PostConstruct;
import java.util.ArrayDeque;
import java.util.Optional;

@Slf4j
public class CommandsSessionBot extends TelegramLongPollingBot {

    private String botUserName;
    private String token;
    private final CommandsFactory commandsFactory;
    private final ErrorHandlerFactory errorHandler;
    private final AuthInterceptor authInterceptor;
    private final CommandsSessionBotProperties properties;
    private final Sinks.Many<Update> commandsSinks = Sinks.many().unicast().onBackpressureBuffer();

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

    @Override
    public void onUpdateReceived(Update update) {
        commandsSinks.tryEmitNext(update);
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
        commandsSinks.asFlux()
            .map(UpdateWrapper::wrap)
            .groupBy(UpdateWrapper::getChatId)
            .flatMap(this::handleUpdates)
            .onErrorResume(errorHandler::handle)
            .subscribe(this::executeMessage);
    }

    private Flux<PartialBotApiMethod<?>> handleUpdates(Flux<UpdateWrapper> updates) {
        Assert.notNull(updates, "Updates is null.");
        return updates
            .scanWith(() -> CommandContext.builder().build(), (context, update) -> {
                if (update.isCommand()) {
                    var updatesDeque = new ArrayDeque<UpdateWrapper>();
                    updatesDeque.add(update);
                    return CommandContext.builder()
                        .commandMessage(update.getMessage())
                        .updates(updatesDeque)
                        .build();
                }
                return context.addUpdate(update);
            })
            .skip(1)
            .flatMap(context -> {
                var commandRequest = getCommandRequest(context);
                if (commandRequest.isEmpty()) {
                    return commandsFactory.getHelpCommand().process(
                        CommandRequest.builder().context(context).build()
                    );
                }
                if (!authInterceptor.intercept(commandRequest.get())) {
                    return Flux.error(new BotAuthException(commandRequest.get(), "User is unauthorized to use bot."));
                }
                return commandsFactory.getCommand(context.getCommand()).process(commandRequest.get());
            });
    }

    private Optional<CommandRequest> getCommandRequest(CommandContext context) {
        if (context == null || context.isEmpty()) {
            return Optional.empty();
        }
        var update = context.getCurrentUpdate();
        if (update.isCommand()) {
            return Optional.of(CommandRequest.builder().context(context).build());
        }
        return update.getArgument()
            .map(argument ->
                CommandRequest.builder()
                    .context(context)
                    .pendingArgument(argument)
                    .build()
            );
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
