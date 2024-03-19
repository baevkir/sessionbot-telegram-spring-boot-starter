
package com.kb.sessionbot.commands;

import com.kb.sessionbot.model.BotCommandResult;
import com.kb.sessionbot.model.CommandContext;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

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
    public Publisher<BotCommandResult> process(CommandContext commandContext) {
        return Mono.fromSupplier(() -> {
            StringBuilder helpMessageBuilder = new StringBuilder("<b>Помощь</b>\n");
            helpMessageBuilder.append("Следующие команды зарегистрированны для бота:\n\n");

            helpMessageBuilder.append(getCommandPresenter(this)).append("\n\n");

            botCommands.stream()
                .filter(Predicate.not(IBotCommand::hidden))
                .forEach(botCommand -> helpMessageBuilder.append(getCommandPresenter(botCommand)).append("\n\n"));

            SendMessage helpMessage = new SendMessage();
            helpMessage.setChatId(commandContext.getChatId());
            helpMessage.enableHtml(true);
            helpMessage.setText(helpMessageBuilder.toString());
            return helpMessage;
        }).map(method -> BotCommandResult.builder().message(method).build());
    }

    private String getCommandPresenter(IBotCommand command) {
            return "<b>" + COMMAND_INIT_CHARACTER + command.getCommandIdentifier() +
                    "</b>\n" + command.getDescription();
    }
}