package com.sessionbot.annotations;

import com.sessionbot.errors.exception.validation.ChatValidationException;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Param {
    int index();
    String displayName();
    Class<? extends ChatValidationException> errorType() default ChatValidationException.class;
}
