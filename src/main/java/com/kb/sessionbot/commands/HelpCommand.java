
package com.kb.sessionbot.commands;

import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class HelpCommand implements IBotCommand {
    public final static String COMMAND_INIT_CHARACTER = "/";

    private final List<IBotCommand> botCommands;

    public HelpCommand(List<IBotCommand> botCommands) {
        this.botCommands = new ArrayList<>(botCommands);
    }

    public List<IBotCommand> getBotCommands() {
        return botCommands;
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
    public Mono<? extends PartialBotApiMethod<?>> process(CommandRequest commandRequest) {
        return Mono.fromSupplier(() -> {
            StringBuilder helpMessageBuilder = new StringBuilder("<b>Помощь</b>\n");
            helpMessageBuilder.append("Следующие команды зарегистрированны для бота:\n\n");

            helpMessageBuilder.append(getCommandPresenter(this)).append("\n\n");

            botCommands.forEach(botCommand -> helpMessageBuilder.append(getCommandPresenter(botCommand)).append("\n\n"));

            SendMessage helpMessage = new SendMessage();
            helpMessage.setChatId(String.valueOf(commandRequest.getCommandMessage().getChat().getId()));
            helpMessage.enableHtml(true);
            helpMessage.setText(helpMessageBuilder.toString());
            return helpMessage;
        });
    }

    private String getCommandPresenter(IBotCommand command) {
            return "<b>" + COMMAND_INIT_CHARACTER + command.getCommandIdentifier() +
                    "</b>\n" + command.getDescription();
    }
}