package com.sessionbot.telegram;

import com.sessionbot.telegram.configurer.CommandsSessionBotProperties;
import com.sessionbot.telegram.errors.handler.ErrorHandlerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class CommandsSessionBot extends TelegramLongPollingBot {

    private CommandsSessionBotProperties botProperties;
    private CommandsFactory commandsFactory;
    private CommandsSessionCash commandsSessionCash;
    private ErrorHandlerFactory errorHandler;

    public CommandsSessionBot(
            CommandsSessionBotProperties botProperties,
            CommandsFactory commandsFactory,
            CommandsSessionCash commandsSessionCash,
            ErrorHandlerFactory errorHandler) {
        this.botProperties = botProperties;
        this.commandsFactory = commandsFactory;
        this.commandsSessionCash = commandsSessionCash;
        this.errorHandler = errorHandler;
    }

    @Override
    public void onUpdateReceived(Update update) {
        Optional<CommandRequest> commandRequest = getCommandRequest(update);

        if (commandRequest.isEmpty()) {
            executeHelp(update);
            return;
        }

        commandsFactory.getCommand(commandRequest.get().getCommand()).process(commandRequest.get())
                .subscribe(
                        answer -> {
                            commandsSessionCash.closeSession(commandRequest.get().getCommandMessage());
                            executeMessage(answer);
                        },
                        error -> errorHandler.handle(error).subscribe(this::executeMessage)
                );
    }

    @Override
    public String getBotToken() {
        return botProperties.getToken();
    }


    @Override
    public String getBotUsername() {
        return botProperties.getBotUsername();
    }

    private Optional<CommandRequest> getCommandRequest(Update update) {
        if (update.hasMessage() && update.getMessage().isCommand()) {
            return Optional.of(registerNewCommandInCash(update.getMessage(), update));
        }
        if (update.hasMessage()) {
            return Optional.ofNullable(commandsSessionCash.getSession(update.getMessage().getFrom().getId(), update.getMessage().getChatId()))
                    .map(cashValue -> toRequest(cashValue)
                            .withUpdate(update)
                            .withPendingArgument(update.getMessage().getText())
                            .build());


        }
        if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            Message message = callbackQuery.getMessage();
            return Optional.ofNullable(commandsSessionCash.getSession(callbackQuery.getFrom().getId(), message.getChatId()))
                    .map(cashValue -> toRequest(cashValue)
                            .withUpdate(update)
                            .withPendingArgument(callbackQuery.getData())
                            .build());
        }
        return Optional.empty();
    }

    private CommandRequest registerNewCommandInCash(Message commandMessage, Update update) {
        Assert.isTrue(commandMessage.isCommand(), "Command should be command.");
        Assert.isTrue(commandMessage.hasText(), "Command should contains text.");

        String commandText = commandMessage.getText().substring(1);
        String[] commandSplit = commandText.split(BotCommand.COMMAND_PARAMETER_SEPARATOR_REGEXP);

        String command = commandSplit[0];
        List<Object> arguments = Stream.of(commandSplit).skip(1).collect(Collectors.toList());

        CommandsSessionCash.SessionValue cashValue = commandsSessionCash.openNewSession(commandMessage, command, arguments);

        return toRequest(cashValue)
                .withUpdate(update)
                .build();
    }

    private CommandRequest.Builder toRequest(CommandsSessionCash.SessionValue cashValue) {
        return CommandRequest.builder()
                .withCommandMessage(cashValue.getCommandMessage())
                .withCommand(cashValue.getCommand())
                .withArguments(new ArrayList<>(cashValue.getArguments()));
    }

    private void executeHelp(Update update) {
        CommandRequest commandRequest = CommandRequest.builder()
                .withCommandMessage(update.getMessage())
                .withUpdate(update).build();
        commandsFactory.getHelpCommand().process(commandRequest).subscribe(this::executeMessage);
    }

    private void executeMessage(BotApiMethod<?> message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Cannot execute message");
        }
    }
}
