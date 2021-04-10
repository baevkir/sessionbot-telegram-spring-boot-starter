package com.sessionbot.commands;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class CommandSessionsHolder {
    private final Map<SessionKey, SessionValue> sessions = new ConcurrentHashMap<>();

    public SessionValue openNewSession(Message commandMessage) {
        Objects.requireNonNull(commandMessage, "command is null");
        Assert.isTrue(commandMessage.isCommand(), "message is not a command");
        Assert.isTrue(commandMessage.hasText(), "Command should contains text.");

        String commandText = commandMessage.getText().substring(1);
        String[] commandSplit = commandText.split(BotCommand.COMMAND_PARAMETER_SEPARATOR_REGEXP);

        String command = commandSplit[0];

        List<Object> arguments = Stream.of(commandSplit).skip(1).collect(Collectors.toList());
        SessionValue cashValue = new SessionValue(commandMessage, command, arguments);

        sessions.put(new SessionKey(commandMessage), cashValue);
        return cashValue;
    }

    public SessionValue getSession(Long userId, Long chatId) {
        Objects.requireNonNull(userId, "userId is null");
        Objects.requireNonNull(chatId, "chatId is null");
        return sessions.get(new SessionKey(userId, chatId));
    }

    public SessionValue updateSessionArguments(Long userId, Long chatId, List<Object> arguments) {
        Objects.requireNonNull(userId, "userId is null");
        Objects.requireNonNull(chatId, "chatId is null");
        Objects.requireNonNull(arguments, "arguments is null");
        SessionValue cashValue = sessions.get(new SessionKey(userId, chatId));
        if (cashValue != null) {
            cashValue.arguments = arguments;
        }
        return cashValue;
    }

    public void closeSession(Message commandMessage) {
        Objects.requireNonNull(commandMessage, "command is null");
        Assert.isTrue(commandMessage.isCommand(), "message is not a command");

        sessions.remove(new SessionKey(commandMessage));
    }

    private static class SessionKey {
        private final Long userId;
        private final Long chatId;

        public SessionKey(Message message) {
            this.userId = message.getFrom().getId();
            this.chatId = message.getChatId();
        }

        public SessionKey(Long userId, Long chatId) {
            this.userId = userId;
            this.chatId = chatId;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            SessionKey that = (SessionKey) object;
            return Objects.equals(userId, that.userId) &&
                    Objects.equals(chatId, that.chatId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, chatId);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SessionValue {
        private final Message commandMessage;
        private final String command;
        private List<Object> arguments;

    }
}
