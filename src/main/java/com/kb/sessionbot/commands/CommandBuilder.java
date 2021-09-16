package com.kb.sessionbot.commands;

import com.google.common.collect.Lists;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.kb.sessionbot.commands.CommandConstants.*;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CommandBuilder {
    public static final String REFRESH_CONTEXT_PARAM = "refreshContext";
    public static final String SCIP_ANSWER_PARAM = "scipAnswer";
    private String command;
    private final List<String> answers = new ArrayList<>();
    private final Map<String, String> params = new HashMap<>();

    public static CommandBuilder create() {
        return new CommandBuilder();
    }

    public CommandBuilder command(String command) {
        this.command = command;
        return this;
    }

    public CommandBuilder addAnswer(String answer) {
        answers.add(answer);
        return this;
    }

    public CommandBuilder addAnswers(List<String> answers) {
        this.answers.addAll(answers);
        return this;
    }

    public CommandBuilder addParam(String param, String value) {
        params.put(param, value);
        return this;
    }


    public CommandBuilder addParam(String param) {
        return addParam(param, null);
    }

    public CommandBuilder refreshContext() {
        return addParam(REFRESH_CONTEXT_PARAM);
    }

    public CommandBuilder scipAnswer() {
        return addParam(SCIP_ANSWER_PARAM);
    }

    public String build() {
        StringBuilder result = new StringBuilder();
        if (StringUtils.isNotEmpty(command)) {
            result.append(COMMAND_START).append(command);
            if(CollectionUtils.isNotEmpty(answers)) {
                result.append(COMMAND_PARAMETERS_SEPARATOR);
            }
        }
        if (CollectionUtils.isNotEmpty(answers)) {
            result.append(String.join(PARAMETER_SEPARATOR, answers));
        }
        if (!params.isEmpty()) {
            result.append(DYNAMIC_PARAMETERS_SEPARATOR).append(params.entrySet().stream()
                .map(entry -> {
                    List<String> values = Lists.newArrayList(entry.getKey());
                    if (entry.getValue() != null) {
                        values.add(entry.getValue());
                    }
                    return String.join(KEY_VALUE_SEPARATOR, values);
                })
                .collect(Collectors.joining(PARAMETER_SEPARATOR)));
        }
        return result.toString();
    }
}
