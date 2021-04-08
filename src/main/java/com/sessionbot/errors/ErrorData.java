package com.sessionbot.errors;

import com.sessionbot.commands.CommandRequest;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class ErrorData {
    private CommandRequest commandRequest;
    private String text;
    private Set<String> options;
}
