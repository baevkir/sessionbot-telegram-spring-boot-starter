package io.github.baevkir.sessionbot.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

class ReplyTest {

    @Test
    void silentCarriesNoMessages() {
        assertThat(Reply.silent().messages()).isEmpty();
    }

    @Test
    void ofKeepsTheMessagesInOrder() {
        var first = SendMessage.builder().chatId("1").text("a").build();
        var second = SendMessage.builder().chatId("1").text("b").build();
        assertThat(Reply.of(first, second).messages()).containsExactly(first, second);
    }

    @Test
    void aLaterChangeToTheCallersListDoesNotReachTheReply() {
        var messages = new ArrayList<PartialBotApiMethod<?>>();
        messages.add(SendMessage.builder().chatId("1").text("a").build());
        var reply = Reply.of(messages);
        messages.clear();
        assertThat(reply.messages()).hasSize(1);
        assertThatThrownBy(() -> reply.messages().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aNullMessageIsRefusedAtCreation() {
        assertThatNullPointerException().isThrownBy(() -> Reply.of((PartialBotApiMethod<?>) null));
        assertThatNullPointerException().isThrownBy(() -> Reply.of(Arrays.asList((PartialBotApiMethod<?>) null)));
    }
}
