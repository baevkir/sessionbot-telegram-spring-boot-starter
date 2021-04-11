package com.kb.sessionbot.commands.errors;

import com.kb.sessionbot.commands.CommandRequest;
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
