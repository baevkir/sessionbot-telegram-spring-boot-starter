package com.kb.sessionbot.guard;

import com.kb.sessionbot.commands.IBotCommand;
import com.kb.sessionbot.model.CommandContext;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;

/** The default {@link GuardDeniedHandler}: the same {@code /help} an unknown command gets. */
public class HelpGuardDeniedHandler implements GuardDeniedHandler {

    private final IBotCommand helpCommand;

    public HelpGuardDeniedHandler(IBotCommand helpCommand) {
        this.helpCommand = helpCommand;
    }

    @Override
    public Publisher<? extends PartialBotApiMethod<?>> onDenied(CommandContext context) {
        return helpCommand.process(context);
    }
}
