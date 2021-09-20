package com.kb.sessionbot.model;

import com.kb.sessionbot.commands.dispatcher.annotations.RenderingOption;
import com.kb.sessionbot.commands.dispatcher.parameters.ParameterRenderer;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Builder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ParameterDescriptor {
    private Parameter parameter;
    private String name;
    private String displayName;
    private Class<?> parameterType;
    private boolean annotated;
    private boolean required;
    private String renderer;
    private Class<? extends ParameterRenderer> rendererType;
    private List<Option> options;

    public static ParameterDescriptorBuilder handleParameter(Parameter parameter) {
        var builder = ParameterDescriptor.builder()
            .parameter(parameter)
            .parameterType(parameter.getType());

        if (parameter.isAnnotationPresent(com.kb.sessionbot.commands.dispatcher.annotations.Parameter.class)) {
            var parameterAnnotation = parameter.getAnnotation(com.kb.sessionbot.commands.dispatcher.annotations.Parameter.class);
            var name = parameterAnnotation.value().isEmpty() ? parameter.getName() : parameterAnnotation.value();
            var fullName = parameterAnnotation.displayName().isEmpty() ? parameter.getName() : parameterAnnotation.displayName();
            builder
                .annotated(true)
                .name(name)
                .displayName(fullName)
                .required(parameterAnnotation.required())
                .renderer(parameterAnnotation.rendering().name())
                .rendererType(parameterAnnotation.rendering().type())
                .options(getRenderingOptions(parameterAnnotation.rendering().options()));

        } else {
            builder.annotated(false)
                .name(parameter.getName())
                .displayName(parameter.getName())
                .required(true);
        }
        return builder;
    }

    private static List<Option> getRenderingOptions(RenderingOption[] renderingOptions) {
        return Arrays.stream(renderingOptions)
            .map(option ->
                Option.builder()
                    .key(option.value())
                    .value(option.displayValue() == null ? option.value() : option.displayValue())
                    .build()
            ).collect(Collectors.toList());

    }
}
