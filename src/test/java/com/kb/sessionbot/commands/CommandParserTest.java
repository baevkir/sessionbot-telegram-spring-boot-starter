package com.kb.sessionbot.commands;


import org.junit.jupiter.api.Test;

import static com.kb.sessionbot.commands.CommandConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class CommandParserTest {

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
        assertThatThrownBy(() -> CommandParser.create(TEST_COMMAND).parseCommand());
    }

    @Test
    void parseCommandOnlyCommand() {
        var command = COMMAND_START + TEST_COMMAND;
        assertThat( CommandParser.create(command).parseCommand()).isEqualTo(TEST_COMMAND);
    }

    @Test
    void parseCommandWithoutArgumentsWithRenderingParams() {
        var command = COMMAND_START + TEST_COMMAND + DYNAMIC_PARAMETERS_SEPARATOR + TEST_RENDERING_PARAMS;
        assertThat( CommandParser.create(command).parseCommand()).isEqualTo(TEST_COMMAND);
    }

    @Test
    void parseCommandWithArgumentsWithRenderingParams() {
        var command = COMMAND_START + TEST_COMMAND + ARGUMENTS_START + TEST_ARGUMENTS + DYNAMIC_PARAMETERS_SEPARATOR + TEST_RENDERING_PARAMS;
        assertThat( CommandParser.create(command).parseCommand()).isEqualTo(TEST_COMMAND);
    }
    @Test
    void parseAnswersEmptyArgs() {
        var command = COMMAND_START + TEST_COMMAND + DYNAMIC_PARAMETERS_SEPARATOR + TEST_RENDERING_PARAMS;
        assertThat( CommandParser.create(command).parseAnswers()).isEmpty();
    }

    @Test
    void parseAnswersWithArgumentsWithRenderingParams() {
        var command = COMMAND_START + TEST_COMMAND + ARGUMENTS_START + TEST_ARGUMENTS + DYNAMIC_PARAMETERS_SEPARATOR + TEST_RENDERING_PARAMS;
        assertThat( CommandParser.create(command).parseAnswers()).hasSize(2).contains(TEST_ARGUMENT1, TEST_ARGUMENT2);
    }

    @Test
    void parseRenderingParamsEmptyRenderingParams() {
        var command = COMMAND_START + TEST_COMMAND + ARGUMENTS_START + TEST_ARGUMENTS;
        assertThat( CommandParser.create(command).parseDynamicParams()).isEmpty();
    }

    @Test
    void parseRenderingParamsWithArgumentsWithRenderingParams() {
        var command = COMMAND_START + TEST_COMMAND + ARGUMENTS_START + TEST_ARGUMENTS + DYNAMIC_PARAMETERS_SEPARATOR + TEST_RENDERING_PARAMS;
        assertThat( CommandParser.create(command).parseDynamicParams()).hasSize(1).containsEntry(TEST_RENDERING_PARAMS_KEY, TEST_RENDERING_PARAMS_VALUE);
    }

    @Test
    void parseRenderingParamsEmptyArgumentsWithRenderingParams() {
        var command = COMMAND_START + TEST_COMMAND + DYNAMIC_PARAMETERS_SEPARATOR + TEST_RENDERING_PARAMS;
        assertThat( CommandParser.create(command).parseDynamicParams()).hasSize(1).containsEntry(TEST_RENDERING_PARAMS_KEY, TEST_RENDERING_PARAMS_VALUE);
    }

    @Test
    void parseRenderingParamsOnlyRenderingParams() {
        var command = DYNAMIC_PARAMETERS_SEPARATOR + TEST_RENDERING_PARAMS;
        assertThat( CommandParser.create(command).parseDynamicParams()).hasSize(1).containsEntry(TEST_RENDERING_PARAMS_KEY, TEST_RENDERING_PARAMS_VALUE);
    }
}