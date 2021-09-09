package com.kb.sessionbot.commands.dispatcher.annotations;

import com.kb.sessionbot.commands.dispatcher.parameters.ParameterRenderer;

public @interface Rendering {
    String name() default "defaultParameterRenderer";
    Class<? extends ParameterRenderer> type() default ParameterRenderer.class;
    String[] options() default {};
}
