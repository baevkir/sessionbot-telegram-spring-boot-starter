package io.github.baevkir.sessionbot.autoconfigure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionBotPropertiesTest {

    @Test
    void noCommandBypassesTheAuthInterceptorByDefault() {
        assertThat(new SessionBotProperties().getPermitCommands()).isEmpty();
    }
}
