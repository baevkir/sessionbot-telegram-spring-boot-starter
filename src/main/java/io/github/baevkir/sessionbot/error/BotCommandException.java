package io.github.baevkir.sessionbot.error;

import io.github.baevkir.sessionbot.CommandContext;
import lombok.Getter;

@Getter
public class BotCommandException extends RuntimeException {
    private final CommandContext context;

    public BotCommandException(CommandContext context, Throwable cause) {
        super(cause);
        this.context = context;
    }
}
