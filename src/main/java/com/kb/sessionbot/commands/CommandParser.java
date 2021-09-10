package com.kb.sessionbot.commands;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;

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
        String[] commandSplit = commandText.split(COMMAND_PARAMETERS_SEPARATOR);
        return commandSplit[0];
    }

    public List<String> parseAnswers() {
        String[] commandSplit = text.split(COMMAND_PARAMETERS_SEPARATOR);
        if (commandSplit.length == 1) {
            return Collections.emptyList();
        }
        var paramsSplit = commandSplit[1].split(RENDERING_PARAMETERS_SEPARATOR);
        return Arrays.asList(paramsSplit[0].split(PARAMETER_SEPARATOR));
    }

    public List<String> parseRenderingParams() {
        var paramsSplit = text.split(RENDERING_PARAMETERS_SEPARATOR);
        if (paramsSplit.length == 1) {
            return Collections.emptyList();
        }
        return Arrays.asList(paramsSplit[1].split(PARAMETER_SEPARATOR));
    }
}
