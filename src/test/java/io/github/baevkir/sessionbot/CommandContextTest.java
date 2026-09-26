package io.github.baevkir.sessionbot;

import io.github.baevkir.sessionbot.fixtures.Fixtures;
import io.github.baevkir.sessionbot.internal.ConversationState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandContextTest {

    @Test
    @DisplayName("of(Update) opens a command context for a command")
    void ofCommandUpdate() {
        var context = CommandContext.of(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/order?buy"));

        assertThat(context.getCommand()).isEqualTo("order");
        assertThat(context.getAnswers()).containsExactly("buy");
        assertThat(context.getChatId()).isEqualTo(String.valueOf(Fixtures.CHAT_ID));
        assertThat(context.getUser().getUserName()).isEqualTo("tester");
        assertThat(context.getCurrentUpdate()).isEmpty();
    }

    @Test
    @DisplayName("of(Update) opens a bare context for anything else")
    void ofBareUpdate() {
        var context = CommandContext.of(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "hello"));

        assertThat(context.getCommand()).isNull();
        assertThat(context.getAnswers()).containsExactly("hello");
        assertThat(context.getCurrentUpdate()).isPresent();
    }

    @Test
    @DisplayName("a bare context exposes its update as the command update, so getCommandUpdate().getFrom() works")
    void bareContextExposesItsUpdateAsCommandUpdate() {
        var context = CommandContext.of(Fixtures.documentUpdate(1, Fixtures.CHAT_ID, 100, "data.csv"));

        assertThat(context.getCommandUpdate()).isNotNull();
        assertThat(context.getCommandUpdate().getFrom().getUserName()).isEqualTo("tester");
        assertThat(context.getUser().getUserName()).isEqualTo("tester");
    }

    @Test
    @DisplayName("getCallbackMessage prefers the latest update's tapped message")
    void callbackMessageFromCurrentUpdate() {
        var context = ConversationState.forCommand(Fixtures.buttonCommandWrapper("/order"))
            .addUpdate(Fixtures.answerWrapper(2, 555, "book"));

        assertThat(context.getCallbackMessage()).get()
            .extracting(message -> message.getMessageId()).isEqualTo(555);
    }

    @Test
    @DisplayName("getCallbackMessage falls back to the command's tapped message")
    void callbackMessageFallsBackToCommand() {
        var context = ConversationState.forCommand(Fixtures.buttonCommandWrapper("/order"))
            .addUpdate(Fixtures.wrap(Fixtures.messageUpdate(2, Fixtures.CHAT_ID, 101, "typed")));

        assertThat(context.getCallbackMessage()).get()
            .extracting(message -> message.getMessageId()).isEqualTo(100);
    }

    @Test
    @DisplayName("getCallbackMessage is empty for a typed command")
    void noCallbackMessageForTypedCommand() {
        assertThat(CommandContext.of(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/order")).getCallbackMessage())
            .isEmpty();
    }

    @Test
    @DisplayName("getAnswers is unmodifiable")
    void answersAreUnmodifiable() {
        var context = CommandContext.of(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/order?buy"));

        assertThatThrownBy(() -> context.getAnswers().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }
}
