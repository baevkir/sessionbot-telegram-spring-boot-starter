
package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.guard.GuardContext;
import io.github.baevkir.sessionbot.i18n.BotLabels;
import io.github.baevkir.sessionbot.CommandContext;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@Slf4j
public class HelpCommand implements RegisteredCommand {
    public final static String COMMAND_INIT_CHARACTER = "/";

    private final List<RegisteredCommand> botCommands;
    private final BotLabels labels;

    public HelpCommand(List<RegisteredCommand> botCommands, BotLabels labels) {
        this.botCommands = new ArrayList<>(botCommands);
        this.labels = labels;
    }

    public List<RegisteredCommand> getBotCommands() {
        return botCommands;
    }

    @Override
    public String getCommandIdentifier() {
        return "help";
    }

    @Override
    public String getDescription(String userName) {
        return labels.helpDescription(userName);
    }

    @Override
    public Publisher<? extends PartialBotApiMethod<?>> process(CommandContext commandContext) {
        var userName = userName(commandContext);
        return Flux.fromIterable(botCommands)
            .filter(Predicate.not(RegisteredCommand::hidden))
            .concatMap(botCommand -> CommandGuards.permits(botCommand, GuardContext.of(commandContext, botCommand.getCommandIdentifier()))
                .filter(Boolean::booleanValue)
                .map(permitted -> botCommand))
            .collectList()
            .map(visibleCommands -> {
                StringBuilder helpMessageBuilder = new StringBuilder("<b>").append(labels.helpTitle(commandContext)).append("</b>\n");
                helpMessageBuilder.append(labels.helpIntro(commandContext)).append("\n\n");
                helpMessageBuilder.append(getCommandPresenter(this, userName)).append("\n\n");
                visibleCommands.forEach(botCommand -> helpMessageBuilder.append(getCommandPresenter(botCommand, userName)).append("\n\n"));
                return SendMessage.builder()
                    .chatId(commandContext.getChatId())
                    .parseMode(ParseMode.HTML)
                    .text(helpMessageBuilder.toString())
                    .build();
            });
    }

    private String getCommandPresenter(RegisteredCommand command, String userName) {
            return "<b>" + COMMAND_INIT_CHARACTER + TelegramHtml.escape(command.getCommandIdentifier()) +
                    "</b>\n" + TelegramHtml.escape(command.getDescription(userName));
    }

    private String userName(CommandContext context) {
        return Optional.ofNullable(context.getCommandUpdate())
            .or(context::getCurrentUpdate)
            .map(update -> update.getFrom())
            .map(user -> user.getUserName())
            .orElse(null);
    }
}