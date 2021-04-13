package com.kb.sessionbot.commands;

import com.kb.sessionbot.commands.auth.AuthInterceptor;
import com.kb.sessionbot.commands.errors.exception.BotAuthException;
import com.kb.sessionbot.commands.errors.handler.ErrorHandlerFactory;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Optional;

@Slf4j
public class CommandsSessionBot extends TelegramLongPollingBot {

    private String botUserName;
    private String token;
    private final CommandsFactory commandsFactory;
    private final CommandSessionsHolder commandSessionsHolder;
    private final ErrorHandlerFactory errorHandler;
    private final AuthInterceptor authInterceptor;

    public CommandsSessionBot(
            CommandsFactory commandsFactory,
            CommandSessionsHolder commandSessionsHolder,
            AuthInterceptor authInterceptor,
            ErrorHandlerFactory errorHandler) {
        this.commandsFactory = commandsFactory;
        this.commandSessionsHolder = commandSessionsHolder;
        this.errorHandler = errorHandler;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void onUpdateReceived(Update update) {
        Mono.defer(() -> {
            var commandRequest = getCommandRequest(update);
            if (commandRequest.isEmpty()) {
                return commandsFactory.getHelpCommand().process(
                        CommandRequest.builder()
                                .commandMessage(update.getMessage())
                                .update(update)
                                .arguments(Collections.emptyList())
                                .build()
                );
            }
            if (!authInterceptor.intercept(commandRequest.get())) {
                throw new BotAuthException(commandRequest.get(), "User is unauthorized to use bot.");
            }
            return commandsFactory.getCommand(commandRequest.get().getCommand()).process(commandRequest.get());
        }).subscribe(
                this::executeMessage,
                error -> errorHandler.handle(error).subscribe(this::executeMessage)
        );
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public String getBotUsername() {
        return botUserName;
    }

    private Optional<CommandRequest> getCommandRequest(Update update) {
        if (update.hasMessage() && update.getMessage().isCommand()) {
            return Optional.of(
                    CommandRequest.toRequest(commandSessionsHolder.openNewSession(update.getMessage()))
                            .update(update)
                            .build()
            );
        }
        if (update.hasMessage()) {
            return Optional.ofNullable(commandSessionsHolder.getSession(update.getMessage().getFrom().getId(), update.getMessage().getChatId()))
                    .map(cashValue -> CommandRequest.toRequest(cashValue)
                            .update(update)
                            .pendingArgument(update.getMessage().getText())
                            .build());


        }
        if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            Message message = callbackQuery.getMessage();
            return Optional.ofNullable(commandSessionsHolder.getSession(callbackQuery.getFrom().getId(), message.getChatId()))
                    .map(cashValue -> CommandRequest.toRequest(cashValue)
                            .update(update)
                            .pendingArgument(callbackQuery.getData())
                            .build());
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
