package com.kb.sessionbot.commands.dispatcher.annotations;

import com.kb.sessionbot.commands.dispatcher.parameters.ParameterRenderer;

public @interface RenderingOption {
    String value();
    String displayValue() default "";
}
