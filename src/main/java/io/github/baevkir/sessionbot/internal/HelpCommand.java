package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.guard.GuardContext;
import io.github.baevkir.sessionbot.i18n.BotLabels;
import io.github.baevkir.sessionbot.CommandContext;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.User;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
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
    public String getDescription(User user) {
        return labels.helpDescription(user);
    }

    @Override
    public Publisher<? extends PartialBotApiMethod<?>> process(ConversationState conversation) {
        conversation.close();
        return render(conversation);
    }

    /** The help message for {@code commandContext}'s caller, listing only commands their guards permit. */
    public Publisher<? extends PartialBotApiMethod<?>> render(CommandContext commandContext) {
        var user = commandContext.getUser();
        return Flux.fromIterable(botCommands)
            .filter(Predicate.not(RegisteredCommand::hidden))
            .concatMap(botCommand -> CommandGuards.permits(botCommand, GuardContext.of(commandContext, botCommand.getCommandIdentifier()))
                .filter(Boolean::booleanValue)
                .map(permitted -> botCommand))
            .collectList()
            .map(visibleCommands -> {
                StringBuilder helpMessageBuilder = new StringBuilder("<b>").append(labels.helpTitle(commandContext)).append("</b>\n");
                helpMessageBuilder.append(labels.helpIntro(commandContext)).append("\n\n");
                helpMessageBuilder.append(getCommandPresenter(this, user)).append("\n\n");
                visibleCommands.forEach(botCommand -> helpMessageBuilder.append(getCommandPresenter(botCommand, user)).append("\n\n"));
                return SendMessage.builder()
                    .chatId(commandContext.getChatId())
                    .parseMode(ParseMode.HTML)
                    .text(helpMessageBuilder.toString())
                    .build();
            });
    }

    private String getCommandPresenter(RegisteredCommand command, User user) {
            return "<b>" + COMMAND_INIT_CHARACTER + TelegramHtml.escape(command.getCommandIdentifier()) +
                    "</b>\n" + TelegramHtml.escape(command.getDescription(user));
    }
}