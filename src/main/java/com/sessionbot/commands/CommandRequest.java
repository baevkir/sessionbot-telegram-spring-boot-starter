package com.sessionbot.commands;

import lombok.Builder;
import lombok.Getter;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Getter
@Builder
public class CommandRequest {
    private final Message commandMessage;
    private final Update update;
    private final String command;
    private final List<Object> arguments;
    private final Object pendingArgument;

}
