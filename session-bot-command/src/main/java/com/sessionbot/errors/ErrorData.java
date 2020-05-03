package com.sessionbot.errors;

import com.sessionbot.CommandRequest;
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
