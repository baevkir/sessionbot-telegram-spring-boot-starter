package com.kb.sessionbot.commands.model;

import com.kb.sessionbot.commands.CommandParser;
import lombok.*;
import org.springframework.util.Assert;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

@ToString
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CommandContext {

    private Message commandMessage;
    private String command;
    private final Deque<UpdateWrapper> updates = new ArrayDeque<>();
    private final List<Object> answers = new CopyOnWriteArrayList<>();
    private final Map<String, String> renderingParams = Collections.synchronizedMap(new HashMap<>());

    public static CommandContext create(UpdateWrapper commandUpdate) {
        Assert.isTrue(commandUpdate.isCommand(), "Context should be created only for command.");
        CommandContext context = new CommandContext();
        context.commandMessage = commandUpdate.getMessage();
        context.updates.add(commandUpdate);

        CommandParser parser = CommandParser.parse(context.commandMessage.getText());
        context.renderingParams.putAll(parser.getRenderingParams());
        context.command = parser.getCommand();
        context.answers.addAll(parser.getAnswers());
        return context;
    }

    public static CommandContext empty() {
        return new CommandContext();
    }

    public CommandContext addAnswer(Object answer) {
        answers.add(answer);
        return this;
    }

    public CommandContext addRenderingParam(String key, String value) {
        renderingParams.put(key, value);
        return this;
    }

    public CommandContext addRenderingParam(String... params) {
        Assert.notNull(params, "Params is null.");
        Assert.isTrue(params.length == 2, () -> "Param has wrong format " + Arrays.toString(params));
        renderingParams.put(params[0], params[1]);
        return this;
    }
    public CommandContext addUpdate(UpdateWrapper update) {
        Assert.isTrue(!update.isCommand(), "Command should create new context");
        updates.add(update);
        return this;
    }

    public boolean isEmpty() {
        return commandMessage == null;
    }


    public String getChatId() {
        return Optional.ofNullable(commandMessage)
                .map(Message::getChatId)
                .map(String::valueOf)
                .orElse(null);
    }

    public UpdateWrapper getCurrentUpdate() {
        return updates.getLast();
    }
}
