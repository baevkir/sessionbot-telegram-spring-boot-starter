package com.sessionbot.commands.dispatcher.parameters;

import com.sessionbot.commands.CommandRequest;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ParameterRequest {
    private final CommandRequest commandRequest;
    private final String text;
    private final Set<String> options;
}
