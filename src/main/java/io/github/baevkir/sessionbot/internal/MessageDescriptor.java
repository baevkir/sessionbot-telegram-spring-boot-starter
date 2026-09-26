package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.DynamicParameters;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static io.github.baevkir.sessionbot.internal.CommandConstants.*;

/**
 * Parses the callback/command wire format
 * {@code /command[@addressee][?answer1&answer2][#param:value&flag]} in one left-to-right pass: the
 * first {@code #} starts the dynamic parameters, the first {@code ?} after a command starts the
 * answers. Wire text comes from callback data the bot built itself; what a user types goes through
 * {@link #parseTyped} instead, so typed text can never forge extra answers or control parameters.
 */
@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MessageDescriptor {
    private String command;
    private String addressee;
    private List<String> answers;
    private DynamicParameters dynamicParams;

    public static MessageDescriptor parse(String text) {
        Assert.isTrue(StringUtils.hasText(text), "text is empty");
        var descriptor = new MessageDescriptor();
        var paramsStart = text.indexOf(DYNAMIC_PARAMETERS_SEPARATOR);
        var head = paramsStart < 0 ? text : text.substring(0, paramsStart);
        var params = paramsStart < 0 ? "" : text.substring(paramsStart + DYNAMIC_PARAMETERS_SEPARATOR.length());
        var isCommand = head.startsWith(COMMAND_START);
        var answersPart = head;
        if (isCommand) {
            var answersStart = head.indexOf(COMMAND_PARAMETERS_SEPARATOR);
            var name = answersStart < 0 ? head.substring(COMMAND_START.length()) : head.substring(COMMAND_START.length(), answersStart);
            answersPart = answersStart < 0 ? "" : head.substring(answersStart + COMMAND_PARAMETERS_SEPARATOR.length());
            var addresseeStart = name.indexOf(ADDRESSEE_SEPARATOR);
            descriptor.command = addresseeStart < 0 ? name : name.substring(0, addresseeStart);
            descriptor.addressee = addresseeStart < 0 ? null
                : StringUtils.hasText(name.substring(addresseeStart + 1)) ? name.substring(addresseeStart + 1) : null;
        }
        descriptor.answers = parseAnswers(answersPart, isCommand);
        descriptor.dynamicParams = parseDynamicParams(params);
        log.debug("Parsed '{}' -> command={} addressee={} answers={} params={}",
            text, descriptor.command, descriptor.addressee, descriptor.answers, descriptor.dynamicParams);
        return descriptor;
    }

    /**
     * Parses a message the user typed. A typed command keeps its command, addressee and
     * {@code ?}-answers but never carries dynamic parameters; any other text is one answer, verbatim.
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
        descriptor.answers = List.of();
        descriptor.dynamicParams = DynamicParameters.empty();
        return descriptor;
    }

    public boolean isCommand() {
        return command != null;
    }

    private static List<String> parseAnswers(String answersPart, boolean isCommand) {
        var hasAnswers = isCommand ? !answersPart.isEmpty() : StringUtils.hasText(answersPart);
        if (!hasAnswers) {
            return List.of();
        }
        return Arrays.stream(answersPart.split(PARAMETER_SEPARATOR)).map(WireFormat::decode).toList();
    }

    private static DynamicParameters parseDynamicParams(String params) {
        if (params.isEmpty()) {
            return DynamicParameters.empty();
        }
        return DynamicParameters.create(
            Arrays.stream(params.split(PARAMETER_SEPARATOR))
                .filter(param -> !param.isEmpty())
                .map(param -> param.split(KEY_VALUE_SEPARATOR, 2))
                .collect(Collectors.toMap(
                    param -> WireFormat.decode(param[ 0 ]),
                    param -> param.length > 1 ? WireFormat.decode(param[ 1 ]) : "",
                    (first, last) -> last))
        );
    }
}
