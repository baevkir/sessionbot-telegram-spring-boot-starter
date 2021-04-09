
package com.sessionbot.commands;

import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class HelpCommand implements IBotCommand {

    private final List<IBotCommand> botCommands;

    public HelpCommand(List<IBotCommand> botCommands) {
        this.botCommands = new ArrayList<>(botCommands);
    }

    @Override
    public String getCommandIdentifier() {
        return "help";
    }

    @Override
    public String getDescription() {
        return "Получить список доступных команд.";
    }

    @Override
    public Mono<? extends BotApiMethod<?>> process(CommandRequest commandRequest) {
        return Mono.fromSupplier(() -> {
            StringBuilder helpMessageBuilder = new StringBuilder("<b>Помощь</b>\n");
            helpMessageBuilder.append("Следующие команды зарегистрированны для бота:\n\n");

            helpMessageBuilder.append(toString()).append("\n\n");

            botCommands.forEach(botCommand -> helpMessageBuilder.append(botCommand.toString()).append("\n\n"));

            SendMessage helpMessage = new SendMessage();
            helpMessage.setChatId(commandRequest.getCommandMessage().getChat().getId());
            helpMessage.enableHtml(true);
            helpMessage.setText(helpMessageBuilder.toString());
            return helpMessage;
        });
    }

}