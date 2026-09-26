package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.fixtures.EchoCommand;
import io.github.baevkir.sessionbot.fixtures.Fixtures;
import io.github.baevkir.sessionbot.fixtures.OrderCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MethodMatcherTest {

    private final MethodMatcher orderMatcher = MethodMatcher.create(new OrderCommand());
    private final MethodMatcher echoMatcher = MethodMatcher.create(new EchoCommand());

    private static ConversationState contextWith(String wire) {
        return ConversationState.forCommand(Fixtures.commandWrapper(wire));
    }

    @Nested
    @DisplayName("literal + placeholder matching")
    class Matching {

        @Test
        void literalThenPlaceholderMatchesTemplate() {
            var match = orderMatcher.getMatchingMethod(contextWith("/order?buy&book"));
            assertThat(match).isPresent();
            assertThat(match.get().getArguments()).isEqualTo("buy&{product}");
        }

        @Test
        void literalPrefixSelectsAmongCompetingTemplates() {
            var match = orderMatcher.getMatchingMethod(contextWith("/order?qty&5"));
            assertThat(match).isPresent();
            assertThat(match.get().getArguments()).isEqualTo("qty&{count}");
        }

        @Test
        void partialAnswersStillMatchByLiteralPrefix() {
            // Only the literal answer present; placeholder not yet supplied.
            var match = orderMatcher.getMatchingMethod(contextWith("/order?buy"));
            assertThat(match).isPresent();
            assertThat(match.get().getArguments()).isEqualTo("buy&{product}");
        }

        @Test
        void unknownLiteralMatchesNothing() {
            assertThat(orderMatcher.getMatchingMethod(contextWith("/order?unknown"))).isEmpty();
        }

        @Test
        void tooManyAnswersFilteredOutByTemplateSize() {
            // 3 answers but the buy template has size 2 -> filtered by args.size() <= template.size().
            assertThat(orderMatcher.getMatchingMethod(contextWith("/order?buy&book&extra"))).isEmpty();
        }
    }

    @Nested
    @DisplayName("default method")
    class DefaultMethod {

        @Test
        void selectedWhenNoAnswers() {
            var match = echoMatcher.getMatchingMethod(contextWith("/echo"));
            assertThat(match).isPresent();
            assertThat(match.get().getArguments()).isEqualTo("");
            assertThat(match.get().isDefaultMethod()).isTrue();
        }

        @Test
        void notSelectedWhenAnswersPresent() {
            var match = echoMatcher.getMatchingMethod(contextWith("/echo?wrapCommand"));
            assertThat(match).isPresent();
            assertThat(match.get().getArguments()).isEqualTo("wrapCommand");
        }
    }

    @Nested
    @DisplayName("duplicate template detection")
    class DuplicateTemplates {

        @io.github.baevkir.sessionbot.annotation.BotCommand(value = "dup", description = "dup")
        static class DuplicateCommand {
            @io.github.baevkir.sessionbot.annotation.CommandMethod(arguments = "buy&{x}")
            public org.telegram.telegrambots.meta.api.methods.send.SendMessage one(
                @io.github.baevkir.sessionbot.annotation.Parameter("x") String x) {
                return org.telegram.telegrambots.meta.api.methods.send.SendMessage.builder().chatId("1").text(x).build();
            }
            @io.github.baevkir.sessionbot.annotation.CommandMethod(arguments = "buy&{x}")
            public org.telegram.telegrambots.meta.api.methods.send.SendMessage two(
                @io.github.baevkir.sessionbot.annotation.Parameter("x") String x) {
                return org.telegram.telegrambots.meta.api.methods.send.SendMessage.builder().chatId("1").text(x).build();
            }
        }

        @Test
        void duplicateTemplateThrowsNamingClassAndTemplate() {
            assertThatThrownBy(() -> MethodMatcher.create(new DuplicateCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DuplicateCommand")
                .hasMessageContaining("buy&{x}");
        }
    }
}