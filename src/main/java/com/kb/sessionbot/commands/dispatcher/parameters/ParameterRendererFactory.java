package com.kb.sessionbot.commands.dispatcher.parameters;

import com.kb.sessionbot.commands.dispatcher.annotations.RenderingMethod;
import com.kb.sessionbot.model.ParameterDescriptor;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ParameterRendererFactory {
    private Map<String, ParameterRenderer> parameterRendererBeansMap;
    private Collection<Object> handlers;
    private ParameterRendererFactory parentFactory;

    private Map<String, ParameterRenderer> parameterRenderers;
    private ParameterRenderer defaultParameterRenderer;

    public static ParameterRendererFactory create(Map<String, ParameterRenderer> parameterRendererBeansMap, Collection<Object> handlers) {
        ParameterRendererFactory factory = new ParameterRendererFactory();
        factory.parameterRendererBeansMap = parameterRendererBeansMap;
        factory.handlers = handlers;
        return factory;
    }

    public static ParameterRendererFactory createChild(Object handler, ParameterRendererFactory parentFactory) {
        ParameterRendererFactory factory = new ParameterRendererFactory();
        factory.parameterRendererBeansMap = Collections.emptyMap();
        factory.handlers = Collections.singleton(handler);
        factory.parentFactory = parentFactory;
        factory.init();
        return factory;
    }

    public ParameterRenderer getRenderer(ParameterDescriptor parameter) {
        var renderer = getRendererInternal(parameter);
        if (renderer != null) {
            return renderer;
        }
        return Optional.ofNullable(parentFactory)
            .map(factory -> factory.getRendererInternal(parameter))
            .orElseThrow(() -> {
                var searchName = Optional.ofNullable(parameter.getParameterType()).map(Class::getSimpleName).orElse(parameter.getRenderer());
                return new RuntimeException("Cannot find ParameterRenderer of " + searchName);
            });
    }

    public ParameterRenderer getDefaultRenderer() {
        return Optional.ofNullable(parentFactory)
            .map(ParameterRendererFactory::getDefaultRenderer)
            .orElse(defaultParameterRenderer);
    }

    @PostConstruct
    public void init() {
        var result = new HashMap<>(parameterRendererBeansMap);
        handlers.forEach(handler -> {
            Arrays.stream(handler.getClass().getMethods())
                .filter(method -> method.isAnnotationPresent(RenderingMethod.class))
                .peek(method -> log.debug("Find RenderingMethod {} for class {}.", method, handler.getClass()))
                .forEach(method -> {
                    var name = method.getAnnotation(RenderingMethod.class).value();
                    result.put(name, new RendererDispatcher(handler, method));
                });
        });
        defaultParameterRenderer = result.get("defaultParameterRenderer");
        parameterRenderers = result;
    }

    private ParameterRenderer getRendererInternal(ParameterDescriptor parameter) {
        if (parameter.getRendererType() != ParameterRenderer.class) {
            return parameterRenderers.values()
                .stream()
                .filter(bean -> parameter.getRendererType().equals(AopUtils.getTargetClass(bean)))
                .findFirst()
                .orElse(null);
        }
        return parameterRenderers.get(parameter.getRenderer());
    }
}