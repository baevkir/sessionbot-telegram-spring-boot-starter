package com.kb.sessionbot.commands;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.kb.sessionbot.commands.CommandConstants.*;

@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MessageDescriptor {
    private String command;
    private List<String> answers;
    private Map<String, String> dynamicParams;

    public static MessageDescriptor parse(String text) {
        Assert.isTrue(StringUtils.hasText(text), "text is empty");
        MessageDescriptor parser = new MessageDescriptor();
        parser.command = parseCommand(text);
        parser.answers = parseAnswers(text);
        parser.dynamicParams = parseDynamicParams(text);
        return parser;
    }

    public boolean isCommand() {
        return command != null;
    }

    public boolean needRefreshContext() {
        return dynamicParams.containsKey(REFRESH_CONTEXT_DYNAMIC_PARAM);
    }

    public boolean canScipAnswer() {
        return BooleanUtils.toBoolean(getDynamicParams().getOrDefault(SCIP_ANSWER_DYNAMIC_PARAM, "false"));
    }

    public boolean commandApproved() {
        return dynamicParams.containsKey(APPROVED_DYNAMIC_PARAM);
    }

    public String getInitiator() {
        return dynamicParams.get(INITIATOR_DYNAMIC_PARAM);
    }

    private static String parseCommand(String text) {
        if (text.startsWith(COMMAND_START)) {
            String commandText = text.substring(1);
            if (commandText.contains(DYNAMIC_PARAMETERS_SEPARATOR)) {
                commandText = commandText.substring(0, commandText.indexOf(DYNAMIC_PARAMETERS_SEPARATOR));
            }
            String[] commandSplit = commandText.split(COMMAND_PARAMETERS_SEPARATOR_REGEX);
            return commandSplit[0];
        }
        return null;
    }

    private static List<String> parseAnswers(String text) {
        if (!text.startsWith(COMMAND_START)) {
            var paramsSplit = text.split(DYNAMIC_PARAMETERS_SEPARATOR);
            if (StringUtils.hasText(paramsSplit[0])) {
                return Arrays.asList(paramsSplit[0].split(PARAMETER_SEPARATOR));
            }
            return Collections.emptyList();
        }
        String[] commandSplit = text.split(COMMAND_PARAMETERS_SEPARATOR_REGEX);
        if (commandSplit.length == 1) {
            return Collections.emptyList();
        }
        var paramsSplit = commandSplit[1].split(DYNAMIC_PARAMETERS_SEPARATOR);
        return Arrays.asList(paramsSplit[0].split(PARAMETER_SEPARATOR));
    }

    private static Map<String, String> parseDynamicParams(String text) {
        var paramsSplit = text.split(DYNAMIC_PARAMETERS_SEPARATOR);
        if (paramsSplit.length == 1) {
            return Collections.emptyMap();
        }
        return Arrays.stream(paramsSplit[1].split(PARAMETER_SEPARATOR))
            .map(params -> params.split(KEY_VALUE_SEPARATOR))
            .collect(Collectors.toMap(params -> params[0], params -> params.length > 1 ? params[1] : ""));
    }


}
