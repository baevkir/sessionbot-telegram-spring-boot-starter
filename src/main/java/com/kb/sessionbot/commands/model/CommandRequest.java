package com.kb.sessionbot.commands.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.telegram.telegrambots.meta.api.objects.Update;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CommandRequest {
    private final CommandContext context;
    private final Object pendingArgument;

    public UpdateWrapper getUpdate() {
        return context.getCurrentUpdate();
    }

}

