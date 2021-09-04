package com.kb.sessionbot.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.*;

@ToString
@Getter
@Builder
public class CommandContext {
    private final Message commandMessage;
    private Deque<Update> updates;
    private final Map<Integer, Object> answers = Collections.synchronizedMap(new LinkedHashMap<>());

    public CommandContext addAnswer(Message message, Object answer) {
        answers.put(message.getMessageId(), answer);
        return this;
    }

    public CommandContext addUpdate(Update update) {
        updates.add(update);
        return this;
    }

    public boolean isEmpty() {
        return commandMessage == null;
    }

    public String getCommand() {
        String commandText = commandMessage.getText().substring(1);
        String[] commandSplit = commandText.split(BotCommand.COMMAND_PARAMETER_SEPARATOR_REGEXP);
        return commandSplit[0];
    }

    public String getChatId() {
        return Optional.ofNullable(commandMessage)
                .map(Message::getChatId)
                .map(String::valueOf)
                .orElse(null);
    }

    public Update getCurrentUpdate() {
        return updates.getLast();
    }
}
