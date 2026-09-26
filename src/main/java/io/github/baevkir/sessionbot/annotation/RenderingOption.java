package io.github.baevkir.sessionbot.annotation;


public @interface RenderingOption {
    String value();
    String displayValue() default "";
}
