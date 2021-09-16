package com.kb.sessionbot.model;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;

@Slf4j
@EqualsAndHashCode(of = "arguments")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Builder
public class MethodDescriptor {
    private String arguments;
    private List<String> template;
    private Map<String, Integer> placeholderIndexes;
    private Method method;
    private List<ParameterDescriptor> parameters;

    public boolean isDefaultMethod() {
        return arguments.equals("");
    }
}
