package io.github.baevkir.sessionbot.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A predefined answer offered as a button; only usable inside {@link Rendering#options()}. */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface RenderingOption {
    String value();
    String displayValue() default "";
}
