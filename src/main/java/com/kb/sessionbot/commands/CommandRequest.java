package com.kb.sessionbot.commands;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;
import java.util.Optional;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CommandRequest {
    private final CommandContext context;
    private final Update update;
    private final Object pendingArgument;

}
