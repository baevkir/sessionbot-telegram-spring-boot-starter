package io.github.baevkir.sessionbot.guard;

import io.github.baevkir.sessionbot.fixtures.Fixtures;
import io.github.baevkir.sessionbot.CommandContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuardContextTest {

    @Test
    void aCommandMessageCarriesItsSenderChatAndType() {
        GuardContext context = GuardContext.of(Fixtures.contextFor("/admin"), "admin");

        assertThat(context.user().getUserName()).isEqualTo("tester");
        assertThat(context.chatId()).isEqualTo(String.valueOf(Fixtures.CHAT_ID));
        assertThat(context.chatType()).isEqualTo("private");
        assertThat(context.command()).isEqualTo("admin");
    }

    @Test
    void aButtonTapCarriesTheChatTypeOfTheTappedMessage() {
        var tap = CommandContext.create(Fixtures.wrap(Fixtures.callbackUpdate(1, Fixtures.CHAT_ID, 50, "/admin")));

        assertThat(GuardContext.of(tap, "admin").chatType()).isEqualTo("private");
    }

    @Test
    void anEmptyContextFallsBackToTheCurrentUpdate() {
        var context = CommandContext.empty()
            .addUpdate(Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "hello")));

        GuardContext guardContext = GuardContext.of(context, "help");

        assertThat(guardContext.user().getUserName()).isEqualTo("tester");
        assertThat(guardContext.chatId()).isEqualTo(String.valueOf(Fixtures.CHAT_ID));
    }

    @Test
    void aContextWithNoUpdateAtAllHasNoUser() {
        GuardContext guardContext = GuardContext.of(CommandContext.empty(), "help");

        assertThat(guardContext.user()).isNull();
        assertThat(guardContext.chatId()).isNull();
        assertThat(guardContext.command()).isEqualTo("help");
    }
}
