package com.sessionbot;

import com.sessionbot.commands.HelpCommand;
import com.sessionbot.commands.BotCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class CommandsFactory {
    private HelpCommand helpCommand;
    private List<BotCommand> botCommands;

    private final Map<String, BotCommand> commandRegistryMap = new HashMap<>();

    public CommandsFactory(HelpCommand helpCommand, List<BotCommand> botCommands) {
        this.helpCommand = helpCommand;
        this.botCommands = botCommands;
    }

    public final BotCommand getHelpCommand() {
        return helpCommand;
    }

    public final BotCommand getCommand(String commandName) {
        return commandRegistryMap.getOrDefault(commandName, helpCommand);
    }

    @PostConstruct
    public void start() {
        botCommands.forEach(command -> commandRegistryMap.put(command.getCommandIdentifier(), command));
    }
}
