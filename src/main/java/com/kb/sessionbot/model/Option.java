package com.kb.sessionbot.model;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class Option {
    private String key;
    private String value;
}
