package com.kb.sessionbot.commands;

import com.kb.sessionbot.fixtures.Fixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandsFactoryTest {

    @Test
    void resolvesRegisteredCommandsAndFallsBackToHelp() {
        var order = command("order");
        var factory = factory(List.of(order));

        assertThat(factory.getCommand("order")).isSameAs(order);
        assertThat(factory.getCommand("unknown")).isSameAs(factory.getHelpCommand());
    }

    @Test
    void duplicateIdentifiersFailStartup() {
        var factory = new CommandsFactory(help(), List.of(command("order"), command("order")));

        assertThatThrownBy(factory::start)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("'order'");
    }

    @Test
    void aCommandNamedHelpFailsStartup() {
        var factory = new CommandsFactory(help(), List.of(command("help")));

        assertThatThrownBy(factory::start)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("/help");
    }

    private static CommandsFactory factory(List<IBotCommand> commands) {
        var factory = new CommandsFactory(help(), commands);
        factory.start();
        return factory;
    }

    private static HelpCommand help() {
        return new HelpCommand(List.of(), Fixtures.labels(Locale.ENGLISH));
    }

    private static IBotCommand command(String identifier) {
        IBotCommand command = mock(IBotCommand.class);
        when(command.getCommandIdentifier()).thenReturn(identifier);
        return command;
    }
}
