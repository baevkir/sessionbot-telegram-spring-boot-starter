package io.github.baevkir.sessionbot.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramHtmlTest {

    @Test
    void escapesOnlyTheCharactersTelegramRequires() {
        assertThat(TelegramHtml.escape("a < b & c > d")).isEqualTo("a &lt; b &amp; c &gt; d");
    }

    @Test
    void leavesAccentsCyrillicAndQuotesAlone() {
        assertThat(TelegramHtml.escape("café \"Кухня\" it's")).isEqualTo("café \"Кухня\" it's");
    }

    @Test
    void nullStaysNull() {
        assertThat(TelegramHtml.escape(null)).isNull();
    }
}
