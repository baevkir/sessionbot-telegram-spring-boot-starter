package io.github.baevkir.sessionbot;

import io.github.baevkir.sessionbot.internal.WireFormat;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.github.baevkir.sessionbot.internal.CommandConstants.*;

/**
 * Builds the callback/command wire string ({@code /command?answer1&answer2#param:value})
 * from a command, answers and dynamic parameters. Warns when the result exceeds Telegram's
 * 64-byte callback-data limit.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CommandBuilder {

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

    public CommandBuilder addAnswer(Long answer) {
        answers.add(String.valueOf(answer));
        return this;
    }

    public CommandBuilder addAnswer(boolean answer) {
        answers.add(Boolean.toString(answer));
        return this;
    }

    public CommandBuilder addAnswer(LocalDate answer) {
        if (answer == null) {
            answers.add(NULL_ANSWER);
        } else {
            answers.add(answer.format(DateTimeFormatter.ISO_DATE));
        }
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
        return addParam(REFRESH_CONTEXT_DYNAMIC_PARAM);
    }

    public CommandBuilder skipAnswer(int index) {
        return addParam(SKIP_ANSWER_DYNAMIC_PARAM, String.valueOf(index));
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
            result.append(answers.stream().map(WireFormat::encodeAnswer).collect(Collectors.joining(PARAMETER_SEPARATOR)));
        }
        if (!params.isEmpty()) {
            result.append(DYNAMIC_PARAMETERS_SEPARATOR).append(params.entrySet().stream()
                .map(entry -> {
                    var key = WireFormat.encodeParam(entry.getKey());
                    return entry.getValue() == null ? key : key + KEY_VALUE_SEPARATOR + WireFormat.encodeParam(entry.getValue());
                })
                .collect(Collectors.joining(PARAMETER_SEPARATOR)));
        }
        var callback = result.toString();
        var byteLength = callback.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength > MAX_CALLBACK_BYTES) {
            log.warn("Callback data is {} bytes, exceeding the {}-byte Telegram limit and cannot be used as callback data.",
                byteLength, MAX_CALLBACK_BYTES);
        }
        return callback;
    }
}
