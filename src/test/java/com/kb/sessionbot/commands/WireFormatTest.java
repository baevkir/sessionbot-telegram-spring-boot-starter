package com.kb.sessionbot.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class WireFormatTest {

    @ParameterizedTest
    @ValueSource(strings = {"plain", "Tom & Jerry", "a:b", "#tag", "why?", "50%", "%26 literal", "Пилосос & підлога"})
    void decodeReversesEncode(String value) {
        assertThat(WireFormat.decode(WireFormat.encodeAnswer(value))).isEqualTo(value);
        assertThat(WireFormat.decode(WireFormat.encodeParam(value))).isEqualTo(value);
    }

    @Test
    void onlyReservedCharactersAreEscaped() {
        assertThat(WireFormat.encodeParam("a b-c_d.e")).isEqualTo("a b-c_d.e");
        assertThat(WireFormat.encodeParam("%?&#:")).isEqualTo("%25%3F%26%23%3A");
    }

    @Test
    void answersKeepTheirColonsReadable() {
        assertThat(WireFormat.encodeAnswer("19:30")).isEqualTo("19:30");
        assertThat(WireFormat.encodeAnswer("%?&#")).isEqualTo("%25%3F%26%23");
    }

    @Test
    void nullStaysNull() {
        assertThat(WireFormat.encodeAnswer(null)).isNull();
        assertThat(WireFormat.encodeParam(null)).isNull();
        assertThat(WireFormat.decode(null)).isNull();
    }
}
