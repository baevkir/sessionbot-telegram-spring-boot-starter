package com.sessionbot.commands.dispatcher.annotations;

import com.sessionbot.commands.dispatcher.parameters.ParameterRenderer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Parameter {
    int index();
    String name();
    String rendering() default "defaultParameterRenderer";
    Class<? extends ParameterRenderer> renderingType();
}
