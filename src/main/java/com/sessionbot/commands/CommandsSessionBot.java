package com.sessionbot.commands;

import com.sessionbot.errors.handler.ErrorHandlerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class CommandsSessionBot extends TelegramLongPollingBot {

    private String botUserName;
    private String token;
    private final CommandsFactory commandsFactory;
    private final CommandsSessionCash commandsSessionCash;
    private final ErrorHandlerFactory errorHandler;

    public CommandsSessionBot(
            CommandsFactory commandsFactory,
            CommandsSessionCash commandsSessionCash,
            ErrorHandlerFactory errorHandler) {
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
        return token;
    }


    @Override
    public String getBotUsername() {
        return botUserName;
    }

    private Optional<CommandRequest> getCommandRequest(Update update) {
        if (update.hasMessage() && update.getMessage().isCommand()) {
            return Optional.of(registerNewCommandInCash(update.getMessage(), update));
        }
        if (update.hasMessage()) {
            return Optional.ofNullable(commandsSessionCash.getSession(update.getMessage().getFrom().getId(), update.getMessage().getChatId()))
                    .map(cashValue -> toRequest(cashValue)
                            .update(update)
                            .pendingArgument(update.getMessage().getText())
                            .build());


        }
        if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            Message message = callbackQuery.getMessage();
            return Optional.ofNullable(commandsSessionCash.getSession(callbackQuery.getFrom().getId(), message.getChatId()))
                    .map(cashValue -> toRequest(cashValue)
                            .update(update)
                            .pendingArgument(callbackQuery.getData())
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
                .update(update)
                .build();
    }

    private CommandRequest.CommandRequestBuilder toRequest(CommandsSessionCash.SessionValue cashValue) {
        return CommandRequest.builder()
                .commandMessage(cashValue.getCommandMessage())
                .command(cashValue.getCommand())
                .arguments(new ArrayList<>(cashValue.getArguments()));
    }

    private void executeHelp(Update update) {
        CommandRequest commandRequest = CommandRequest.builder()
                .commandMessage(update.getMessage())
                .update(update)
                .arguments(Collections.emptyList())
                .build();
        commandsFactory.getHelpCommand().process(commandRequest).subscribe(this::executeMessage);
    }

    private void executeMessage(BotApiMethod<?> message) {
        try {
            execute(message);
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
