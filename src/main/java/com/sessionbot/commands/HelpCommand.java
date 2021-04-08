
package com.sessionbot.commands;

import com.sessionbot.annotations.CommandMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class HelpCommand extends ReactiveBotCommand {

    private List<BotCommand> botCommands;

    @Autowired
    public HelpCommand(List<BotCommand> botCommands) {
        super("help", "Получить список доступных команд.");
        this.botCommands = new ArrayList<>(botCommands);
    }

    @CommandMethod
    public Mono<? extends BotApiMethod<?>> process(Message command) {
        return Mono.fromSupplier(() -> {
            StringBuilder helpMessageBuilder = new StringBuilder("<b>Помощь</b>\n");
            helpMessageBuilder.append("Следующие команды зарегистрированны для бота:\n\n");

            helpMessageBuilder.append(toString()).append("\n\n");

            botCommands.forEach(botCommand -> helpMessageBuilder.append(botCommand.toString()).append("\n\n"));

            SendMessage helpMessage = new SendMessage();
            helpMessage.setChatId(command.getChat().getId());
            helpMessage.enableHtml(true);
            helpMessage.setText(helpMessageBuilder.toString());
            return helpMessage;
        });
    }
}