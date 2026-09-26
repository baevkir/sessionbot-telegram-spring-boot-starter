package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.annotation.Parameter;
import io.github.baevkir.sessionbot.annotation.Rendering;
import io.github.baevkir.sessionbot.annotation.RenderingOption;
import io.github.baevkir.sessionbot.render.ParameterOption;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParameterDescriptorTest {

    @SuppressWarnings("unused")
    void pick(@Parameter(value = "size", rendering = @Rendering(options = {
        @RenderingOption(value = "s", displayValue = "Small"),
        @RenderingOption("m")
    })) String size) {
    }

    @Test
    void renderingOptionsBecomeParameterOptionsWithLabelsDefaultingToTheValue() throws Exception {
        var parameter = getClass().getDeclaredMethod("pick", String.class).getParameters()[0];

        var descriptor = ParameterDescriptor.handleParameter(parameter).build();

        assertThat(descriptor.getOptions()).containsExactly(
            new ParameterOption("s", "Small"),
            new ParameterOption("m", "m"));
    }
}
