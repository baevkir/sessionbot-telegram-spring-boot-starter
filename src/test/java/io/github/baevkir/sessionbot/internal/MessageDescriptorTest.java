package io.github.baevkir.sessionbot.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageDescriptorTest {

    @Nested
    @DisplayName("command detection")
    class CommandDetection {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> command={1}")
        @CsvSource({
            "/order,             true,  order",
            "/order?buy&book,    true,  order",
            "/order#k:v,         true,  order",
            "/order?buy#k:v,     true,  order",
            "order,              false, ",
            "buy&book,           false, ",
            "#k:v,               false, "
        })
        void parsesCommandFlagAndName(String text, boolean isCommand, String expectedCommand) {
            var descriptor = MessageDescriptor.parse(text);
            assertThat(descriptor.isCommand()).isEqualTo(isCommand);
            assertThat(descriptor.getCommand()).isEqualTo(expectedCommand);
        }
    }

    @Nested
    @DisplayName("answers parsing")
    class Answers {

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @CsvSource({
            "/order,            0",
            "/order?,           0",
            "/order?buy,        1",
            "/order?buy&book,   2",
            "/order#k:v,        0",
            "buy&book,          2",
            "#k:v,              0"
        })
        void answerCount(String text, int expectedCount) {
            assertThat(MessageDescriptor.parse(text).getAnswers()).hasSize(expectedCount);
        }

        @Test
        void commandWithAnswersAndParams() {
            var descriptor = MessageDescriptor.parse("/order?buy&book#k:v");
            assertThat(descriptor.getAnswers()).containsExactly("buy", "book");
        }

        @Test
        void answersOnlyWithoutLeadingSlash() {
            var descriptor = MessageDescriptor.parse("buy&book");
            assertThat(descriptor.isCommand()).isFalse();
            assertThat(descriptor.getAnswers()).containsExactly("buy", "book");
        }

        @Test
        void trailingArgumentSeparatorYieldsNoAnswers() {
            // "/order?".split("\\?") -> ["/order"], length 1 -> empty answers.
            assertThat(MessageDescriptor.parse("/order?").getAnswers()).isEmpty();
        }

        @Test
        void aWhitespaceOnlyCommandAnswerIsKept() {
            assertThat(MessageDescriptor.parse("/order?   ").getAnswers()).containsExactly("   ");
        }

        @Test
        void aWhitespaceOnlyBareTextYieldsNoAnswers() {
            assertThat(MessageDescriptor.parse("   #k").getAnswers()).isEmpty();
        }
    }

    @Nested
    @DisplayName("dynamic params parsing")
    class DynamicParams {

        @Test
        void noParamsYieldsEmptyMap() {
            assertThat(MessageDescriptor.parse("/order?buy").getDynamicParams().getParams()).isEmpty();
        }

        @Test
        void singleParamWithValue() {
            assertThat(MessageDescriptor.parse("/order#k:v").getDynamicParams().getParams())
                .containsExactlyEntriesOf(java.util.Map.of("k", "v"));
        }

        @Test
        void paramWithoutValueBecomesEmptyString() {
            assertThat(MessageDescriptor.parse("/order#refreshContext").getDynamicParams().getParams())
                .containsEntry("refreshContext", "");
        }

        @Test
        void multipleDynamicParams() {
            assertThat(MessageDescriptor.parse("/order#a:1&b:2&flag").getDynamicParams().getParams())
                .containsEntry("a", "1")
                .containsEntry("b", "2")
                .containsEntry("flag", "")
                .hasSize(3);
        }

        @Test
        void paramsOnlyWithoutCommandOrAnswers() {
            var descriptor = MessageDescriptor.parse("#k:v");
            assertThat(descriptor.isCommand()).isFalse();
            assertThat(descriptor.getCommand()).isNull();
            assertThat(descriptor.getAnswers()).isEmpty();
            assertThat(descriptor.getDynamicParams().getParams()).containsEntry("k", "v");
        }
    }

    @Nested
    @DisplayName("typed text")
    class TypedText {

        @Test
        void plainTextIsOneVerbatimAnswer() {
            var descriptor = MessageDescriptor.parseTyped("Tom & Jerry #approved");
            assertThat(descriptor.isCommand()).isFalse();
            assertThat(descriptor.getAnswers()).containsExactly("Tom & Jerry #approved");
            assertThat(descriptor.getDynamicParams().isEmpty()).isTrue();
        }

        @Test
        void typedCommandKeepsAnswersButDropsDynamicParams() {
            var descriptor = MessageDescriptor.parseTyped("/order?buy&book#approved&initiator:admin");
            assertThat(descriptor.getCommand()).isEqualTo("order");
            assertThat(descriptor.getAnswers()).containsExactly("buy", "book");
            assertThat(descriptor.getDynamicParams().isEmpty()).isTrue();
        }

        @Test
        void textThatLooksLikeASkipControlIsJustAnAnswer() {
            var descriptor = MessageDescriptor.parseTyped("x#skip:abc");
            assertThat(descriptor.getAnswers()).containsExactly("x#skip:abc");
            assertThat(descriptor.getDynamicParams().canSkipAnswer(0)).isFalse();
        }
    }

    @Nested
    @DisplayName("callback robustness")
    class CallbackRobustness {

        @Test
        void repeatedDynamicParamKeepsTheLastValue() {
            assertThat(MessageDescriptor.parse("#k:1&k:2").getDynamicParams().getParams())
                .containsExactly(java.util.Map.entry("k", "2"));
        }

        @Test
        void valueMayContainAnEscapedSeparator() {
            assertThat(MessageDescriptor.parse("#time:10%3A30").getDynamicParams().getParam("time")).isEqualTo("10:30");
        }

        @Test
        void unknownPercentSequenceIsLeftAsIs() {
            assertThat(MessageDescriptor.parse("50%zz").getAnswers()).containsExactly("50%zz");
        }
    }

    @Nested
    @DisplayName("guard cases")
    class Guards {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   "})
        void blankInputThrows(String text) {
            assertThatThrownBy(() -> MessageDescriptor.parse(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("text is empty");
        }
    }

    @Nested
    @DisplayName("grammar")
    class Grammar {

        @Test
        void commandCarriesItsAddressee() {
            var descriptor = MessageDescriptor.parse("/order@MyBot?buy&book");
            assertThat(descriptor.getCommand()).isEqualTo("order");
            assertThat(descriptor.getAddressee()).isEqualTo("MyBot");
            assertThat(descriptor.getAnswers()).containsExactly("buy", "book");
        }

        @Test
        void commandWithoutAddresseeHasNone() {
            assertThat(MessageDescriptor.parse("/order?buy").getAddressee()).isNull();
        }

        @Test
        void anEmptyAddresseeCountsAsNone() {
            var descriptor = MessageDescriptor.parse("/order@?x");
            assertThat(descriptor.getCommand()).isEqualTo("order");
            assertThat(descriptor.getAddressee()).isNull();
            assertThat(descriptor.getAnswers()).containsExactly("x");
        }

        @Test
        void aSecondQuestionMarkStaysInTheAnswer() {
            assertThat(MessageDescriptor.parse("/a?b?c").getAnswers()).containsExactly("b?c");
        }

        @Test
        void aLoneHashParsesToNothing() {
            var descriptor = MessageDescriptor.parse("#");
            assertThat(descriptor.isCommand()).isFalse();
            assertThat(descriptor.getAnswers()).isEmpty();
            assertThat(descriptor.getDynamicParams().isEmpty()).isTrue();
        }

        @Test
        void aLoneSlashIsAnEmptyCommand() {
            var descriptor = MessageDescriptor.parse("/");
            assertThat(descriptor.isCommand()).isTrue();
            assertThat(descriptor.getCommand()).isEmpty();
        }

        @Test
        void typedCommandKeepsItsAddressee() {
            var descriptor = MessageDescriptor.parseTyped("/order@MyBot#approved");
            assertThat(descriptor.getAddressee()).isEqualTo("MyBot");
            assertThat(descriptor.getDynamicParams().isEmpty()).isTrue();
        }
    }
}