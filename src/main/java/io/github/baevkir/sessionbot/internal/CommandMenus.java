package io.github.baevkir.sessionbot.internal;

import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;

import java.util.List;

/** Turns commands into Telegram menu entries. */
public final class CommandMenus {

    private CommandMenus() {
    }

    /**
     * The menu every chat sees. A guard is per caller and this scope is shared, so a guarded command is
     * never listed here - it appears only in a chat-scoped menu set by {@link CommandMenuService}.
     */
    public static List<BotCommand> defaultCommands(List<RegisteredCommand> commands) {
        return toBotCommands(commands.stream()
            .filter(command -> !command.hidden() && command.guards().isEmpty())
            .toList(), null);
    }

    public static List<BotCommand> toBotCommands(List<RegisteredCommand> commands, String userName) {
        return commands.stream()
            .<BotCommand>map(command -> BotCommand.builder()
                .command(command.getCommandIdentifier())
                .description(command.getDescription(userName))
                .build())
            .toList();
    }
}
