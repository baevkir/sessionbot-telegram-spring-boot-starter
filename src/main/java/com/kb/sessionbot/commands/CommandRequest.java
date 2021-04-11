package com.kb.sessionbot.commands;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CommandRequest {
    private final Message commandMessage;
    private final Update update;
    private final String command;
    private final List<Object> arguments;
    private final Object pendingArgument;

    public static CommandRequest.CommandRequestBuilder toRequest(CommandSessionsHolder.SessionValue cashValue) {
        return CommandRequest.builder()
                .commandMessage(cashValue.getCommandMessage())
                .command(cashValue.getCommand())
                .arguments(cashValue.getArguments());
    }
}
