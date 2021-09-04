package com.kb.sessionbot.commands;

import com.kb.sessionbot.commands.auth.AuthInterceptor;
import com.kb.sessionbot.commands.errors.exception.BotAuthException;
import com.kb.sessionbot.commands.errors.handler.ErrorHandlerFactory;
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
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import javax.annotation.PostConstruct;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class CommandsSessionBot extends TelegramLongPollingBot {

    private String botUserName;
    private String token;
    private final CommandsFactory commandsFactory;
    private final ErrorHandlerFactory errorHandler;
    private final AuthInterceptor authInterceptor;
    private final Sinks.Many<Update> commandsSinks = Sinks.many().unicast().onBackpressureBuffer();

    public CommandsSessionBot(
        CommandsFactory commandsFactory,
        AuthInterceptor authInterceptor,
        ErrorHandlerFactory errorHandler) {
        this.commandsFactory = commandsFactory;
        this.errorHandler = errorHandler;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void onUpdateReceived(Update update) {
        commandsSinks.tryEmitNext(update);
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public String getBotUsername() {
        return botUserName;
    }

    @PostConstruct
    public void init() {
        commandsSinks.asFlux()
            .groupBy(this::getChatId)
            .flatMap(this::handleUpdates)
            .onErrorResume(errorHandler::handle)
            .subscribe(this::executeMessage);
    }

    private Flux<PartialBotApiMethod<?>> handleUpdates(Flux<Update> updates) {
        Assert.notNull(updates, "Updates is null.");
        return updates
            .scanWith(() -> CommandContext.builder().build(), (context, update) -> {
                if (update.hasMessage() && update.getMessage().isCommand()) {
                    return CommandContext.builder()
                        .commandMessage(update.getMessage())
                        .updates(new ArrayDeque<>(Collections.singleton(update)))
                        .build();
                }
                return context.addUpdate(update);
            })
            .skip(1)
            .flatMap(context -> {
                var commandRequest = getCommandRequest(context);
                var update = context.getCurrentUpdate();
                if (commandRequest.isEmpty()) {
                    return commandsFactory.getHelpCommand().process(
                        CommandRequest.builder()
                            .context(CommandContext.builder().commandMessage(update.getMessage()).build())
                            .update(update)
                            .build()
                    );
                }
                if (!authInterceptor.intercept(commandRequest.get())) {
                    return Flux.error(new BotAuthException(commandRequest.get(), "User is unauthorized to use bot."));
                }
                return commandsFactory.getCommand(context.getCommand()).process(commandRequest.get());
            });
    }

    private Long getChatId(Update update) {
        Message message;
        if (update.hasMessage()) {
            message = update.getMessage();
        } else if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            message = callbackQuery.getMessage();
        } else {
            log.error("Cannot get chat id from update.{}", update);
            throw new RuntimeException("Cannot get chat id from update");
        }
        return message.getChatId();
    }

    private Optional<CommandRequest> getCommandRequest(CommandContext context) {
        if (context == null || context.isEmpty()) {
            return Optional.empty();
        }
        var update = context.getCurrentUpdate();
        if (update.hasMessage() && update.getMessage().isCommand()) {
            return Optional.of(
                CommandRequest.builder().context(context).update(update).build()
            );
        }
        if (update.hasMessage()) {
            return Optional.of(
                CommandRequest.builder()
                    .context(context)
                    .update(update)
                    .pendingArgument(update.getMessage().getText())
                    .build()
            );


        }
        if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            return Optional.of(
                CommandRequest.builder()
                    .context(context)
                    .update(update)
                    .pendingArgument(callbackQuery.getData())
                    .build()
            );
        }
        return Optional.empty();
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

    public void setBotUserName(String botUserName) {
        this.botUserName = botUserName;
    }

    public void setToken(String token) {
        this.token = token;
    }


}
