package io.github.baevkir.sessionbot.render;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class Option {
    private String key;
    private String value;
}
