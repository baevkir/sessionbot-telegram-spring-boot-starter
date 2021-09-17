package com.kb.sessionbot.commands.dispatcher;

import com.kb.sessionbot.commands.MessageDescriptor;
import com.kb.sessionbot.commands.dispatcher.annotations.CommandMethod;
import com.kb.sessionbot.model.CommandContext;
import com.kb.sessionbot.model.MethodDescriptor;
import com.kb.sessionbot.model.ParameterDescriptor;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MethodMatcher {
    private static final String PLACEHOLDER_START = "{";
    private static final String PLACEHOLDER_FINISH = "}";
    private Map<String, MethodDescriptor> invokerMethods;

    public static MethodMatcher create(Object command) {
        var methods = Arrays.stream(command.getClass().getMethods())
            .filter(method -> method.isAnnotationPresent(CommandMethod.class))
            .peek(method -> log.debug("Find OperationMethod {} for class {}.", method, command.getClass()))
            .map(method -> {
                var arguments = method.getAnnotation(CommandMethod.class).arguments();
                var builder = MethodDescriptor.builder()
                    .method(method)
                    .arguments(arguments);

                if (StringUtils.hasText(arguments)) {
                    var template = MessageDescriptor.parse(arguments).getAnswers();
                    var placeholders = new HashMap<String, Integer>();
                    for (int index = 0; index < template.size(); index++) {
                        var value = template.get(index);
                        if (isPlaceHolder(value)) {
                            value = value.substring(PLACEHOLDER_START.length(), value.length() - PLACEHOLDER_FINISH.length());
                            placeholders.put(value, index);
                        }
                    }
                    builder.template(template).placeholderIndexes(placeholders);
                } else {
                    builder.template(Collections.emptyList()).placeholderIndexes(Collections.emptyMap());
                }

                var parameters = Arrays.stream(method.getParameters())
                    .map(ParameterDescriptor::handleParameter)
                    .map(ParameterDescriptor.ParameterDescriptorBuilder::build)
                    .collect(Collectors.toList());

                return builder.parameters(parameters).build();
            })
            .collect(Collectors.toMap(MethodDescriptor::getArguments, Function.identity()));

        return new MethodMatcher(methods);
    }

    public Optional<MethodDescriptor> getMatchingMethod(CommandContext commandContext) {
        var defaultMethod = invokerMethods.get("");
        var arguments = commandContext.getAnswers();
        if (defaultMethod != null && arguments.isEmpty()) {
            return Optional.of(defaultMethod);
        }

        return invokerMethods.values()
            .stream()
            .filter(methodDescriptor -> arguments.size() <= methodDescriptor.getTemplate().size())
            .sorted(
                Comparator.comparingInt((MethodDescriptor methodDescriptor) -> methodDescriptor.getTemplate().size())
            )
            .filter(methodDescriptor -> isMethodMatches(methodDescriptor.getTemplate(), arguments))
            .findFirst();
    }

    private boolean isMethodMatches(List<String> template, List<String> args) {
        for (int index = 0; index < template.size(); index++) {
            var templateArg = template.get(index);
            if (isPlaceHolder(templateArg)) {
                continue;
            }
            var arg = args.get(index);
            if (!templateArg.equals(arg)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPlaceHolder(String value) {
        return value.startsWith(PLACEHOLDER_START) && value.endsWith(PLACEHOLDER_FINISH);
    }
}
