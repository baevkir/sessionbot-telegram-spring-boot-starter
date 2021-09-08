package com.kb.sessionbot.commands.model;

import com.google.common.collect.Lists;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;
import java.util.Optional;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CommandRequest {
    private final CommandContext context;
    private final Object pendingArgument;

    public UpdateWrapper getUpdate() {
        return context.getCurrentUpdate();
    }

    public List<Object> getAllAnswers() {
        List<Object> arguments = Lists.newArrayList(context.getAnswers());
        Optional.ofNullable(pendingArgument).ifPresent(arguments::add);
        return arguments;
    }
}

