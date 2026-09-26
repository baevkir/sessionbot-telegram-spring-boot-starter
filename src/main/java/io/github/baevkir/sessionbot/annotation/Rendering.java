package io.github.baevkir.sessionbot.annotation;

import io.github.baevkir.sessionbot.render.ParameterRenderer;

public @interface Rendering {
    String name() default "defaultParameterRenderer";
    Class<? extends ParameterRenderer> type() default ParameterRenderer.class;
    RenderingOption[] options() default {};
}
