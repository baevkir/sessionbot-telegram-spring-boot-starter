package io.github.baevkir.sessionbot.annotation;

import io.github.baevkir.sessionbot.render.ParameterRenderer;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** How a {@link Parameter} is prompted for; only usable as a member of {@code @Parameter}. */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface Rendering {
    String name() default "defaultParameterRenderer";
    Class<? extends ParameterRenderer> type() default ParameterRenderer.class;
    RenderingOption[] options() default {};
}
