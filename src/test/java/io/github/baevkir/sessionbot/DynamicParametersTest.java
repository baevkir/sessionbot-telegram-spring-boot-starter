package io.github.baevkir.sessionbot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicParametersTest {

    @Test
    @DisplayName("empty() has no params and reports false for every flag")
    void emptyHasNoParams() {
        var params = DynamicParameters.empty();
        assertThat(params.isEmpty()).isTrue();
        assertThat(params.needRefreshContext()).isFalse();
        assertThat(params.hasParam("approved")).isFalse();
        assertThat(params.canSkipAnswer(0)).isFalse();
        assertThat(params.getParam("initiator")).isNull();
    }

    @Nested
    @DisplayName("canSkipAnswer")
    class CanSkip {

        @Test
        void falseWhenSkipKeyAbsent() {
            assertThat(DynamicParameters.create(Map.of("other", "1")).canSkipAnswer(0)).isFalse();
        }

        @ParameterizedTest(name = "skip={0}, query index={1} -> {2}")
        @CsvSource({
            "2, 0, true",
            "2, 1, true",
            "2, 2, true",
            "2, 3, false",
            "0, 0, true",
            "0, 1, false"
        })
        void allowsSkipWhenAllowedIndexAtLeastQueried(String allowed, int index, boolean expected) {
            var params = DynamicParameters.create(Map.of("skip", allowed));
            assertThat(params.canSkipAnswer(index)).isEqualTo(expected);
        }

        @Test
        void nonNumericIndexDeniesSkipInsteadOfThrowing() {
            assertThat(DynamicParameters.create(Map.of("skip", "abc")).canSkipAnswer(0)).isFalse();
        }
    }

    @Test
    void needRefreshContextIsKeyPresence() {
        assertThat(DynamicParameters.create(Map.of("refreshContext", "")).needRefreshContext()).isTrue();
        assertThat(DynamicParameters.create(Map.of("x", "y")).needRefreshContext()).isFalse();
    }

    @Test
    void commandApprovedIsKeyPresence() {
        assertThat(DynamicParameters.create(Map.of("approved", "")).hasParam("approved")).isTrue();
        assertThat(DynamicParameters.create(Map.of("x", "y")).hasParam("approved")).isFalse();
    }

    @Test
    void getInitiatorReturnsRawValueOrNull() {
        assertThat(DynamicParameters.create(Map.of("initiator", "alice")).getParam("initiator")).isEqualTo("alice");
        assertThat(DynamicParameters.create(Map.of("x", "y")).getParam("initiator")).isNull();
    }

    @Test
    void hasParamAndGetParam() {
        var params = DynamicParameters.create(Map.of("k", "v"));
        assertThat(params.hasParam("k")).isTrue();
        assertThat(params.hasParam("missing")).isFalse();
        assertThat(params.getParam("k")).isEqualTo("v");
        assertThat(params.getParam("missing")).isNull();
    }
}