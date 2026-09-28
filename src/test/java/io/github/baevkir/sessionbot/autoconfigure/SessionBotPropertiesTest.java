package io.github.baevkir.sessionbot.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SessionBotPropertiesTest {

    @Test
    void noCommandBypassesTheAuthInterceptorByDefault() {
        assertThat(new SessionBotProperties().getPermitCommands()).isEmpty();
    }

    @Test
    void configurationMetadataIsGeneratedForEveryProperty() throws IOException {
        var metadata = new ClassPathResource("META-INF/spring-configuration-metadata.json")
            .getContentAsString(StandardCharsets.UTF_8);

        assertThat(metadata).contains(
            "\"sessionbot.telegram.token\"",
            "\"sessionbot.telegram.bot-username\"",
            "\"sessionbot.telegram.language\"",
            "\"sessionbot.telegram.chat-idle-ttl\"",
            "\"sessionbot.telegram.max-concurrent-chats\"",
            "\"sessionbot.telegram.permit-commands\"");
    }
}
