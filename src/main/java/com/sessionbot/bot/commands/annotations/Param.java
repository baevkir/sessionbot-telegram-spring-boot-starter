package com.sessionbot.bot.commands.annotations;

import com.sessionbot.bot.errors.exception.validation.ChatValidationException;

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
