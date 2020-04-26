package com.sessionbot.telegram.errors;

import com.sessionbot.telegram.CommandRequest;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder(builderClassName = "Builder")
public class ErrorData {
    private CommandRequest commandRequest;
    private String text;
    private Set<String> options;
}
