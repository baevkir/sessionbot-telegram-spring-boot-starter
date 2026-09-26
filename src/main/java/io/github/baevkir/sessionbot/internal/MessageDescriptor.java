package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.DynamicParameters;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static io.github.baevkir.sessionbot.internal.CommandConstants.*;

/**
 * Parses the callback/command wire format ({@code /command?answer1&answer2#param:value})
 * into its command, answers, and dynamic parameters. Wire text comes from callback data the bot
 * built itself; what a user types goes through {@link #parseTyped} instead, so typed text can never
 * forge extra answers or control parameters.
 */
@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MessageDescriptor {
    private String command;
    private List<String> answers;
    private DynamicParameters dynamicParams;

    public static MessageDescriptor parse(String text) {
        Assert.isTrue(StringUtils.hasText(text), "text is empty");
        MessageDescriptor parser = new MessageDescriptor();
        parser.command = parseCommand(text);
        parser.answers = parseAnswers(text);
        parser.dynamicParams = parseDynamicParams(text);
        log.debug("Parsed '{}' -> command={} answers={} params={}", text, parser.command, parser.answers, parser.dynamicParams);
        return parser;
    }

    /**
     * Parses a message the user typed. A typed command keeps its command and {@code ?}-answers but
     * never carries dynamic parameters; any other text is one answer, verbatim.
     */
    public static MessageDescriptor parseTyped(String text) {
        Assert.isTrue(StringUtils.hasText(text), "text is empty");
        MessageDescriptor descriptor;
        if (text.startsWith(COMMAND_START)) {
            descriptor = parse(text);
        } else {
            descriptor = new MessageDescriptor();
            descriptor.answers = List.of(text);
        }
        descriptor.dynamicParams = DynamicParameters.empty();
        return descriptor;
    }

    /** A descriptor for updates with no wire text to parse (e.g. a bare document message). */
    public static MessageDescriptor empty() {
        MessageDescriptor descriptor = new MessageDescriptor();
        descriptor.command = null;
        descriptor.answers = Collections.emptyList();
        descriptor.dynamicParams = DynamicParameters.empty();
        return descriptor;
    }

    public boolean isCommand() {
        return command != null;
    }

    private static String parseCommand(String text) {
        if (text.startsWith(COMMAND_START)) {
            String commandText = text.substring(1);
            if (commandText.contains(DYNAMIC_PARAMETERS_SEPARATOR)) {
                commandText = commandText.substring(0, commandText.indexOf(DYNAMIC_PARAMETERS_SEPARATOR));
            }
            String[] commandSplit = commandText.split(COMMAND_PARAMETERS_SEPARATOR_REGEX);
            return commandSplit[ 0 ];
        }
        return null;
    }

    private static List<String> parseAnswers(String text) {
        if (!text.startsWith(COMMAND_START)) {
            var paramsSplit = text.split(DYNAMIC_PARAMETERS_SEPARATOR);
            if (StringUtils.hasText(paramsSplit[ 0 ])) {
                return decodeAll(paramsSplit[ 0 ].split(PARAMETER_SEPARATOR));
            }
            return Collections.emptyList();
        }
        String[] commandSplit = text.split(COMMAND_PARAMETERS_SEPARATOR_REGEX);
        if (commandSplit.length == 1) {
            return Collections.emptyList();
        }
        var paramsSplit = commandSplit[ 1 ].split(DYNAMIC_PARAMETERS_SEPARATOR);
        return decodeAll(paramsSplit[ 0 ].split(PARAMETER_SEPARATOR));
    }

    private static List<String> decodeAll(String[] values) {
        return Arrays.stream(values).map(WireFormat::decode).toList();
    }

    private static DynamicParameters parseDynamicParams(String text) {
        var paramsSplit = text.split(DYNAMIC_PARAMETERS_SEPARATOR);
        if (paramsSplit.length == 1) {
            return DynamicParameters.empty();
        }
        return DynamicParameters.create(
            Arrays.stream(paramsSplit[ 1 ].split(PARAMETER_SEPARATOR))
                .map(params -> params.split(KEY_VALUE_SEPARATOR, 2))
                .collect(Collectors.toMap(
                    params -> WireFormat.decode(params[ 0 ]),
                    params -> params.length > 1 ? WireFormat.decode(params[ 1 ]) : "",
                    (first, last) -> last))
        );
    }

}
