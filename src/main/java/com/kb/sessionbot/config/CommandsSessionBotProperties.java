package com.kb.sessionbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sessionbot.telegram")
public class CommandsSessionBotProperties {
    private String token;
    private String botUsername;
}
