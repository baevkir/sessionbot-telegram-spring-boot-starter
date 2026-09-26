package com.kb.sessionbot.commands;

import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;
import java.util.*;

@Slf4j
public class CommandsFactory {
    private final HelpCommand helpCommand;
    private final List<IBotCommand> botCommands;

    private final Map<String, IBotCommand> commandRegistryMap = new HashMap<>();

    public CommandsFactory(HelpCommand helpCommand, List<IBotCommand> botCommands) {
        this.helpCommand = helpCommand;
        this.botCommands = botCommands;
    }

    /** Registers the commands, failing startup when two share an identifier or one shadows {@code /help}. */
    @PostConstruct
    public void start() {
        botCommands.forEach(command -> {
            var identifier = command.getCommandIdentifier();
            if (helpCommand.getCommandIdentifier().equals(identifier)) {
                throw new IllegalStateException("Command '" + identifier + "' clashes with the built-in /"
                    + identifier + " command; give it another name");
            }
            if (commandRegistryMap.putIfAbsent(identifier, command) != null) {
                throw new IllegalStateException("Two commands share the identifier '" + identifier + "'");
            }
        });
    }

    public final IBotCommand getHelpCommand() {
        return helpCommand;
    }

    public final IBotCommand getCommand(String commandName) {
        return commandRegistryMap.getOrDefault(commandName, helpCommand);
    }

    public final List<IBotCommand> getCommands() {
        var commands = new ArrayList<IBotCommand>();
        commands.add(helpCommand);
        commands.addAll(botCommands);
        return Collections.unmodifiableList(commands);
    }
}
