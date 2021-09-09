package com.kb.sessionbot.commands;

import com.kb.sessionbot.commands.model.UpdateWrapper;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CommandParser {
    public static final String COMMAND_START = "/";
    public static final String COMMAND_PARAMETERS_SEPARATOR = "\\?";
    public static final String PARAMETER_SEPARATOR = "&";
    public static final String RENDERING_PARAMETERS_SEPARATOR = "#";
    public static final String KEY_VALUE_SEPARATOR = "=";
    private String command;
    private final List<Object> answers = new ArrayList<>();
    private final Map<String, String> renderingParams = new HashMap<>();

    public static CommandParser parse(String commandString) {
        Assert.isTrue(StringUtils.hasText(commandString), "command is empty");
        Assert.isTrue(commandString.startsWith(COMMAND_START), () -> "command has wrong format " + commandString);
        CommandParser parser = new CommandParser();
        String commandText = commandString.substring(1);
        String[] commandSplit = commandText.split(COMMAND_PARAMETERS_SEPARATOR);

        parser.command = commandSplit[0];

        if (commandSplit.length > 1) {
            var paramsSplit = commandSplit[1].split(RENDERING_PARAMETERS_SEPARATOR);
            parser.answers.addAll(Arrays.asList(paramsSplit[ 0 ].split(PARAMETER_SEPARATOR)));

            if (paramsSplit.length > 1) {
                Arrays.stream(paramsSplit[1].split(PARAMETER_SEPARATOR))
                    .map(paramString -> paramString.split(KEY_VALUE_SEPARATOR))
                    .forEach(params -> parser.renderingParams.put(params[0], params[1]));
            }
        }
        return parser;
    }
}
