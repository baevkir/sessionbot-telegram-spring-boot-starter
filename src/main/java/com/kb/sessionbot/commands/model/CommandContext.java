package com.kb.sessionbot.commands.model;

import lombok.*;
import org.springframework.util.Assert;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@ToString
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CommandContext {
    public static final String COMMAND_PARAMETER_SEPARATOR = "\\?";
    public static final String PARAMETER_SEPARATOR = "&";
    private Message commandMessage;
    private String command;
    private final Deque<UpdateWrapper> updates = new ArrayDeque<>();
    private final List<Object> answers = new CopyOnWriteArrayList<>();

    public static CommandContext create(UpdateWrapper commandUpdate) {
        Assert.isTrue(commandUpdate.isCommand(), "Context should be created only for command.");
        CommandContext context = new CommandContext();
        context.commandMessage = commandUpdate.getMessage();

        String commandText = context.commandMessage.getText().substring(1);
        String[] commandSplit = commandText.split(COMMAND_PARAMETER_SEPARATOR);

        context.command = commandSplit[0];
        context.updates.add(commandUpdate);

        if (commandSplit.length > 1) {
            Arrays.asList(commandSplit[1].split(PARAMETER_SEPARATOR)).forEach(context::addAnswer);
        }
        return context;
    }

    public static CommandContext empty() {
        return new CommandContext();
    }

    public CommandContext addAnswer(Object answer) {
        answers.add(answer);
        return this;
    }

    public CommandContext addUpdate(UpdateWrapper update) {
        Assert.isTrue(!update.isCommand(), "Command should create new context");
        updates.add(update);
        return this;
    }

    public boolean isEmpty() {
        return commandMessage == null;
    }


    public String getChatId() {
        return Optional.ofNullable(commandMessage)
                .map(Message::getChatId)
                .map(String::valueOf)
                .orElse(null);
    }

    public UpdateWrapper getCurrentUpdate() {
        return updates.getLast();
    }
}
