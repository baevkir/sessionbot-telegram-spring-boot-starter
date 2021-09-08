package com.kb.sessionbot.commands.dispatcher.annotations;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface BotCommand {
    String value();
    String description() default "";
    boolean hidden() default false;
}
