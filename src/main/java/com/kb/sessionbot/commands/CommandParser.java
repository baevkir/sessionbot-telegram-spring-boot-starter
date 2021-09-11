package com.kb.sessionbot.commands;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.kb.sessionbot.commands.CommandConstants.*;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CommandParser {

    private String text;

    public static CommandParser create(String text) {
        Assert.isTrue(StringUtils.hasText(text), "text is empty");
        CommandParser parser = new CommandParser();
        parser.text = text;
        return parser;
    }

    public String parseCommand() {
        Assert.isTrue(text.startsWith(COMMAND_START), () -> "command has wrong format " + text);
        String commandText = text.substring(1);
        if (commandText.contains(RENDERING_PARAMETERS_SEPARATOR)) {
            commandText = commandText.substring(0, commandText.indexOf(RENDERING_PARAMETERS_SEPARATOR));
        }
        String[] commandSplit = commandText.split(COMMAND_PARAMETERS_SEPARATOR_REGEX);
        return commandSplit[0];
    }

    public List<String> parseAnswers() {
        if (!text.startsWith(COMMAND_START)) {
            var paramsSplit = text.split(RENDERING_PARAMETERS_SEPARATOR);
            return Arrays.asList(paramsSplit[0].split(PARAMETER_SEPARATOR));
        }
        String[] commandSplit = text.split(COMMAND_PARAMETERS_SEPARATOR_REGEX);
        if (commandSplit.length == 1) {
            return Collections.emptyList();
        }
        var paramsSplit = commandSplit[1].split(RENDERING_PARAMETERS_SEPARATOR);
        return Arrays.asList(paramsSplit[0].split(PARAMETER_SEPARATOR));
    }

    public Map<String, String> parseRenderingParams() {
        var paramsSplit = text.split(RENDERING_PARAMETERS_SEPARATOR);
        if (paramsSplit.length == 1) {
            return Collections.emptyMap();
        }
        return Arrays.stream(paramsSplit[1].split(PARAMETER_SEPARATOR))
            .map(params -> params.split(KEY_VALUE_SEPARATOR))
            .collect(Collectors.toMap(params -> params[0], params -> params.length > 1 ? params[1] : ""));
    }
}
