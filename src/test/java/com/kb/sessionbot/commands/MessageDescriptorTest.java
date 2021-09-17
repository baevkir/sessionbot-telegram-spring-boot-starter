package com.kb.sessionbot.commands;


import org.junit.jupiter.api.Test;

import static com.kb.sessionbot.commands.CommandConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class MessageDescriptorTest {

    private static final String ARGUMENTS_START = "?";
    private static final String TEST_COMMAND = "testcommand";
    private static final String TEST_RENDERING_PARAMS_KEY = "testParam";
    private static final String TEST_RENDERING_PARAMS_VALUE = "testValue";
    private static final String TEST_RENDERING_PARAMS = TEST_RENDERING_PARAMS_KEY + KEY_VALUE_SEPARATOR + TEST_RENDERING_PARAMS_VALUE;
    private static final String TEST_ARGUMENT1 = "testArg1";
    private static final String TEST_ARGUMENT2 = "testArg2";
    private static final String TEST_ARGUMENTS = TEST_ARGUMENT1 + PARAMETER_SEPARATOR + TEST_ARGUMENT2;
    @Test
    void parseCommandNotCommand() {
        assertThat(MessageDescriptor.parse(TEST_COMMAND).isCommand()).isFalse();
    }

    @Test
    void parseCommandOnlyCommand() {
        var command = COMMAND_START + TEST_COMMAND;
        var messageDescriptor = MessageDescriptor.parse(command);
        assertThat(messageDescriptor.isCommand()).isTrue();
        assertThat(messageDescriptor.getCommand()).isEqualTo(TEST_COMMAND);
        assertThat( MessageDescriptor.parse(command).getCommand()).isEqualTo(TEST_COMMAND);
    }

    @Test
    void parseCommandWithoutArgumentsWithRenderingParams() {
        var command = COMMAND_START + TEST_COMMAND + DYNAMIC_PARAMETERS_SEPARATOR + TEST_RENDERING_PARAMS;
        var messageDescriptor = MessageDescriptor.parse(command);
        assertThat(messageDescriptor.isCommand()).isTrue();
        assertThat(messageDescriptor.getCommand()).isEqualTo(TEST_COMMAND);
    }

    @Test
    void parseCommandWithArgumentsWithRenderingParams() {
        var command = COMMAND_START + TEST_COMMAND + ARGUMENTS_START + TEST_ARGUMENTS + DYNAMIC_PARAMETERS_SEPARATOR + TEST_RENDERING_PARAMS;
        var messageDescriptor = MessageDescriptor.parse(command);
        assertThat(messageDescriptor.isCommand()).isTrue();
        assertThat(messageDescriptor.getCommand()).isEqualTo(TEST_COMMAND);
    }
    @Test
    void parseAnswersEmptyArgs() {
        var command = COMMAND_START + TEST_COMMAND + DYNAMIC_PARAMETERS_SEPARATOR + TEST_RENDERING_PARAMS;
        var messageDescriptor = MessageDescriptor.parse(command);
        assertThat(messageDescriptor.isCommand()).isTrue();
        assertThat(messageDescriptor.getCommand()).isEqualTo(TEST_COMMAND);
    }

    @Test
    void parseAnswersWithArgumentsWithRenderingParams() {
        var command = COMMAND_START + TEST_COMMAND + ARGUMENTS_START + TEST_ARGUMENTS + DYNAMIC_PARAMETERS_SEPARATOR + TEST_RENDERING_PARAMS;
        var messageDescriptor = MessageDescriptor.parse(command);
        assertThat(messageDescriptor.isCommand()).isTrue();
        assertThat(messageDescriptor.getCommand()).isEqualTo(TEST_COMMAND);
        assertThat(messageDescriptor.getAnswers()).hasSize(2).contains(TEST_ARGUMENT1, TEST_ARGUMENT2);
        assertThat(messageDescriptor.getDynamicParams()).hasSize(1).containsEntry(TEST_RENDERING_PARAMS_KEY, TEST_RENDERING_PARAMS_VALUE);
    }

    @Test
    void parseRenderingParamsEmptyRenderingParams() {
        var command = COMMAND_START + TEST_COMMAND + ARGUMENTS_START + TEST_ARGUMENTS;
        var messageDescriptor = MessageDescriptor.parse(command);
        assertThat(messageDescriptor.isCommand()).isTrue();
        assertThat(messageDescriptor.getCommand()).isEqualTo(TEST_COMMAND);
        assertThat(messageDescriptor.getAnswers()).hasSize(2).contains(TEST_ARGUMENT1, TEST_ARGUMENT2);
        assertThat(messageDescriptor.getDynamicParams()).isEmpty();
    }

    @Test
    void parseRenderingParamsWithArgumentsWithRenderingParams() {
        var command = COMMAND_START + TEST_COMMAND + ARGUMENTS_START + TEST_ARGUMENTS + DYNAMIC_PARAMETERS_SEPARATOR + TEST_RENDERING_PARAMS;
        var messageDescriptor = MessageDescriptor.parse(command);
        assertThat(messageDescriptor.isCommand()).isTrue();
        assertThat(messageDescriptor.getCommand()).isEqualTo(TEST_COMMAND);
        assertThat(messageDescriptor.getAnswers()).isNotEmpty();
        assertThat(messageDescriptor.getDynamicParams()).hasSize(1).containsEntry(TEST_RENDERING_PARAMS_KEY, TEST_RENDERING_PARAMS_VALUE);
    }

    @Test
    void parseRenderingParamsEmptyArgumentsWithRenderingParams() {
        var command = COMMAND_START + TEST_COMMAND + DYNAMIC_PARAMETERS_SEPARATOR + TEST_RENDERING_PARAMS;
        var messageDescriptor = MessageDescriptor.parse(command);
        assertThat(messageDescriptor.isCommand()).isTrue();
        assertThat(messageDescriptor.getCommand()).isEqualTo(TEST_COMMAND);
        assertThat(messageDescriptor.getAnswers()).isEmpty();
        assertThat(messageDescriptor.getDynamicParams()).hasSize(1).containsEntry(TEST_RENDERING_PARAMS_KEY, TEST_RENDERING_PARAMS_VALUE);
    }

    @Test
    void parseRenderingParamsOnlyRenderingParams() {
        var command = DYNAMIC_PARAMETERS_SEPARATOR + TEST_RENDERING_PARAMS;
        var messageDescriptor = MessageDescriptor.parse(command);
        assertThat(messageDescriptor.isCommand()).isFalse();
        assertThat(messageDescriptor.getCommand()).isNull();
        assertThat(messageDescriptor.getAnswers()).isEmpty();
        assertThat(messageDescriptor.getDynamicParams()).hasSize(1).containsEntry(TEST_RENDERING_PARAMS_KEY, TEST_RENDERING_PARAMS_VALUE);
    }
}