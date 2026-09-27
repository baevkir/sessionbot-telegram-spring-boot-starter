package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.fixtures.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationStateTest {

    @Nested
    @DisplayName("creation guards")
    class Guards {

        @Test
        void forCommandRejectsNonCommandUpdate() {
            var answer = Fixtures.answerWrapper(2, 100, "buy&book");
            assertThatThrownBy(() -> ConversationState.forCommand(answer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Context should be created only for command.");
        }

        @Test
        void forBareUpdateRejectsCommandUpdate() {
            assertThatThrownBy(() -> ConversationState.forBareUpdate(Fixtures.commandWrapper("/order")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A command opens a command context");
        }

        @Test
        void addUpdateRejectsCommandUpdate() {
            var context = ConversationState.forCommand(Fixtures.commandWrapper("/order"));
            var command = Fixtures.commandWrapper("/order");
            assertThatThrownBy(() -> context.addUpdate(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Command should create new context");
        }
    }

    @Test
    @DisplayName("empty() has no command and no chat; forCommand() is open")
    void emptyAndCommandState() {
        var empty = ConversationState.empty();
        assertThat(empty.hasCommand()).isFalse();
        assertThat(empty.getChatId()).isNull();
        assertThat(empty.getUser()).isNull();
        assertThat(empty.getDynamicParams().isEmpty()).isTrue();

        var context = ConversationState.forCommand(Fixtures.commandWrapper("/order"));
        assertThat(context.hasCommand()).isTrue();
        assertThat(context.getState()).isEqualTo(ContextState.open);
        assertThat(context.getCommand()).isEqualTo("order");
    }

    @Test
    @DisplayName("getAnswers merges command answers and pending arguments")
    void answersMerge() {
        var context = ConversationState.forCommand(Fixtures.commandWrapper("/order?buy"));
        assertThat(context.getAnswers()).containsExactly("buy");

        context.addUpdate(Fixtures.answerWrapper(2, 100, "book"));
        assertThat(context.getAnswers()).containsExactly("buy", "book");
        assertThat(context.getPendingArguments()).containsExactly("book");
    }

    @Test
    @DisplayName("lifecycle open -> progress -> close")
    void lifecycle() {
        var context = ConversationState.forCommand(Fixtures.commandWrapper("/order"));
        assertThat(context.getState()).isEqualTo(ContextState.open);
        context.startProgress();
        assertThat(context.getState()).isEqualTo(ContextState.progress);
        context.close();
        assertThat(context.getState()).isEqualTo(ContextState.close);
    }

    @Test
    @DisplayName("commitPendingAnswers folds the pending answers in once, then getPendingArguments is empty")
    void commitPendingAnswersFoldsAndClearsPending() {
        var context = ConversationState.forCommand(Fixtures.commandWrapper("/order?buy"));
        context.addUpdate(Fixtures.answerWrapper(2, 100, "book"));
        assertThat(context.getPendingArguments()).containsExactly("book");

        context.commitPendingAnswers(false);

        assertThat(context.getPendingArguments()).isEmpty();
        assertThat(context.getAnswers()).containsExactly("buy", "book");
    }

    @Test
    @DisplayName("commitPendingAnswers adds one empty answer when nothing is pending and skipping is allowed")
    void commitPendingAnswersAddsEmptyAnswerWhenSkipped() {
        var context = ConversationState.forCommand(Fixtures.commandWrapper("/order?note&hello"));
        context.addUpdate(Fixtures.answerWrapper(2, 100, "#skip:2"));
        assertThat(context.getPendingArguments()).isEmpty();

        context.commitPendingAnswers(true);

        assertThat(context.getAnswers()).containsExactly("note", "hello", "");
    }

    @Test
    @DisplayName("addUpdate after a commit starts a fresh pending batch for the new update")
    void addUpdateAfterCommitStartsNewPendingBatch() {
        var context = ConversationState.forCommand(Fixtures.commandWrapper("/order?buy"));
        context.addUpdate(Fixtures.answerWrapper(2, 100, "book"));
        context.commitPendingAnswers(false);

        context.addUpdate(Fixtures.answerWrapper(3, 101, "pen"));

        assertThat(context.getPendingArguments()).containsExactly("pen");
        assertThat(context.getAnswers()).containsExactly("buy", "book", "pen");
    }

    @Test
    @DisplayName("refreshContext rebuild keeps command answers and re-applies the latest update")
    void refreshRebuildSemantics() {
        var command = Fixtures.commandWrapper("/order?buy");
        var refreshUpdate = Fixtures.answerWrapper(2, 100, "book#refreshContext");
        var rebuilt = ConversationState.forCommand(command).addUpdate(refreshUpdate);

        assertThat(rebuilt.getCommand()).isEqualTo("order");
        assertThat(rebuilt.getAnswers()).containsExactly("buy", "book");
        assertThat(rebuilt.getDynamicParams().needRefreshContext()).isTrue();
    }

    @Test
    @DisplayName("getDynamicParams reads the current update, falling back to the command update")
    void dynamicParamsFallback() {
        var context = ConversationState.forCommand(Fixtures.buttonCommandWrapper("/order#approved"));
        assertThat(context.getDynamicParams().hasParam("approved")).isTrue();

        context.addUpdate(Fixtures.answerWrapper(2, 100, "book#initiator:alice"));
        assertThat(context.getDynamicParams().getParam("initiator")).isEqualTo("alice");
    }

    @Test
    @DisplayName("addQuestionMessage rejects null and records the message")
    void questionMessages() {
        var context = ConversationState.forCommand(Fixtures.commandWrapper("/order"));
        assertThatThrownBy(() -> context.addQuestionMessage(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Message is null");
        var message = Fixtures.message(Fixtures.CHAT_ID, 555, "question");
        context.addQuestionMessage(message);
        assertThat(context.getQuestionMessages()).containsExactly(message);
    }

    @Test
    @DisplayName("a bare state carries its update as both opening and current update")
    void bareState() {
        var update = Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "hello"));
        var context = ConversationState.forBareUpdate(update);

        assertThat(context.hasCommand()).isFalse();
        assertThat(context.getCommandUpdate()).isSameAs(update);
        assertThat(context.getCurrentUpdate()).containsSame(update);
        assertThat(context.getChatId()).isEqualTo(String.valueOf(Fixtures.CHAT_ID));
    }
}
