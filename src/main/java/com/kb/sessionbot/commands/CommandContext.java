package com.kb.sessionbot.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@ToString
@Getter
@Builder
public class CommandContext {
    private final Message commandMessage;
    private final Map<Integer, Object> answers = Collections.synchronizedMap(new LinkedHashMap<>());

    public void addAnswer(Message message, Object answer) {
        answers.put(message.getMessageId(), answer);
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
}
