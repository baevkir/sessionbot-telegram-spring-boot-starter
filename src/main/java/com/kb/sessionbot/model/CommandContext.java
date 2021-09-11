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

        CommandParser parser = CommandParser.create(commandUpdate.getText().orElse(""));
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

    public List<String> getPendingArguments() {
        return getCurrentUpdate()
            .flatMap(UpdateWrapper::getArguments)
            .map(CommandParser::create)
            .map(CommandParser::parseAnswers)
            .orElse(Collections.emptyList());
    }

    public String getChatId() {
        return Optional.ofNullable(commandUpdate)
            .or(this::getCurrentUpdate)
            .map(UpdateWrapper::getChatId)
            .orElse(null);
    }

    public Optional<UpdateWrapper> getInitialUpdate() {
        return Optional.ofNullable(updates.peekFirst());
    }

    public Optional<UpdateWrapper> getCurrentUpdate() {
        return Optional.ofNullable(updates.peekLast());
    }

    public List<Object> getAnswers() {
        return ImmutableList.builder()
            .addAll(answers)
            .addAll(getPendingArguments())
            .build();
    }

    public Map<String, String> getRenderingParameters() {
        return getCurrentUpdate().orElse(commandUpdate).getRenderingParameters();
    }
}
