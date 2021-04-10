package com.sessionbot.commands;

import com.sessionbot.commands.errors.handler.ErrorHandlerFactory;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Collections;
import java.util.Optional;

@Slf4j
public class CommandsSessionBot extends TelegramLongPollingBot {

    private String botUserName;
    private String token;
    private final CommandsFactory commandsFactory;
    private final CommandSessionsHolder commandSessionsHolder;
    private final ErrorHandlerFactory errorHandler;

    public CommandsSessionBot(
            CommandsFactory commandsFactory,
            CommandSessionsHolder commandSessionsHolder,
            ErrorHandlerFactory errorHandler) {
        this.commandsFactory = commandsFactory;
        this.commandSessionsHolder = commandSessionsHolder;
        this.errorHandler = errorHandler;
    }

    @Override
    public void onUpdateReceived(Update update) {
        Optional<CommandRequest> commandRequest = getCommandRequest(update);

        if (commandRequest.isEmpty()) {
            executeHelp(update);
            return;
        }

        commandsFactory.getCommand(commandRequest.get().getCommand()).process(commandRequest.get()).subscribe(
                this::executeMessage,
                error -> errorHandler.handle(error).subscribe(this::executeMessage),
                () -> commandSessionsHolder.closeSession(commandRequest.get().getCommandMessage())
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

    private void executeHelp(Update update) {
        CommandRequest commandRequest = CommandRequest.builder()
                .commandMessage(update.getMessage())
                .update(update)
                .arguments(Collections.emptyList())
                .build();
        commandsFactory.getHelpCommand().process(commandRequest).subscribe(this::executeMessage);
    }

    private void executeMessage(PartialBotApiMethod<?> message) {
        try {
            if (message instanceof BotApiMethod) {
                execute((BotApiMethod<?>) message);
            } else {
                throw new UnsupportedOperationException("Message type " + message.getClass().getSimpleName() + " is not supported yet");
            }
        } catch (TelegramApiException e) {
            log.error("Cannot execute message");
        }
    }

    public void setBotUserName(String botUserName) {
        this.botUserName = botUserName;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
