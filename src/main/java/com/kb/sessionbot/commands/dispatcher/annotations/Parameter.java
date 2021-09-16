package com.kb.sessionbot.commands.dispatcher.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Parameter {
    String value() default "";
    String displayName() default "";
    boolean required() default true;
    Rendering rendering() default @Rendering;
}
