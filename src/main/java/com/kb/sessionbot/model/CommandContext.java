package com.kb.sessionbot.model;

import com.google.common.collect.ImmutableList;
import com.kb.sessionbot.commands.CommandParser;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.util.Assert;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.*;

@ToString
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CommandContext {

    private UpdateWrapper commandUpdate;
    private String command;
    private final Deque<UpdateWrapper> updates = new ArrayDeque<>();
    private final List<String> answers = Collections.synchronizedList(new ArrayList<>());

    public static CommandContext create(UpdateWrapper commandUpdate) {
        Assert.isTrue(commandUpdate.isCommand(), "Context should be created only for command.");
        CommandContext context = new CommandContext();
        context.commandUpdate = commandUpdate;
        context.updates.add(commandUpdate);

        CommandParser parser = CommandParser.create(commandUpdate.getMessage().getText());
        context.command = parser.parseCommand();
        context.answers.addAll(parser.parseAnswers());

        return context;
    }

    public static CommandContext empty() {
        return new CommandContext();
    }

    public Message getCommandMessage() {
        return commandUpdate.getMessage();
    }

    public CommandContext addAnswer(String answer) {
        answers.add(answer);
        return this;
    }

    public CommandContext addUpdate(UpdateWrapper update) {
        Assert.isTrue(!update.isCommand(), "Command should create new context");
        updates.add(update);
        return this;
    }

    public boolean isEmpty() {
        return commandUpdate == null;
    }

    public Optional<String> getPendingArgument() {
        return getCurrentUpdate().flatMap(UpdateWrapper::getArgument);
    }

    public String getChatId() {
        return Optional.ofNullable(commandUpdate)
            .map(UpdateWrapper::getMessage)
            .map(Message::getChatId)
            .map(String::valueOf)
            .orElse(null);
    }

    public Optional<UpdateWrapper> getCurrentUpdate() {
        return Optional.ofNullable(updates.peekLast());
    }

    public List<Object> getAnswers() {
        var builder = ImmutableList.builder().addAll(answers);
        getPendingArgument().ifPresent(builder::add);
        return builder.build();
    }

    public List<String> getRenderingParameters() {
        return getCurrentUpdate().orElse(commandUpdate).getRenderingParameters();
    }
}
