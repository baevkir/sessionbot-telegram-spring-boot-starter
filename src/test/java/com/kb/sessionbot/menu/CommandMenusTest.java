package com.kb.sessionbot.menu;

import com.kb.sessionbot.commands.IBotCommand;
import com.kb.sessionbot.guard.CommandGuard;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandMenusTest {

    private static final CommandGuard ALLOW = context -> Mono.just(true);

    @Test
    void theDefaultMenuDropsHiddenAndGuardedCommandsAndKeepsOrder() {
        IBotCommand hidden = command("invite");
        when(hidden.hidden()).thenReturn(true);

        List<BotCommand> menu = CommandMenus.defaultCommands(
            List.of(command("help"), command("admin", ALLOW), hidden, command("tasks")));

        assertThat(menu).extracting(BotCommand::getCommand).containsExactly("help", "tasks");
    }

    @Test
    void theDefaultMenuUsesTheBotWideLanguage() {
        IBotCommand tasks = command("tasks");
        when(tasks.getDescription(isNull())).thenReturn("bot-wide");

        assertThat(CommandMenus.defaultCommands(List.of(tasks)))
            .extracting(BotCommand::getDescription).containsExactly("bot-wide");
    }

    private static IBotCommand command(String identifier, CommandGuard... guards) {
        IBotCommand command = mock(IBotCommand.class);
        when(command.getCommandIdentifier()).thenReturn(identifier);
        when(command.getDescription(isNull())).thenReturn(identifier + " description");
        when(command.guards()).thenReturn(List.of(guards));
        return command;
    }
}
