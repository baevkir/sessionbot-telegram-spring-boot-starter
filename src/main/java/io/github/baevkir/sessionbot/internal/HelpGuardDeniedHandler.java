package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.guard.GuardDeniedHandler;
import io.github.baevkir.sessionbot.CommandContext;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;

/** The default {@link GuardDeniedHandler}: the same {@code /help} an unknown command gets. */
public class HelpGuardDeniedHandler implements GuardDeniedHandler {

    private final RegisteredCommand helpCommand;

    public HelpGuardDeniedHandler(RegisteredCommand helpCommand) {
        this.helpCommand = helpCommand;
    }

    @Override
    public Publisher<? extends PartialBotApiMethod<?>> onDenied(CommandContext context) {
        return helpCommand.process(context);
    }
}
