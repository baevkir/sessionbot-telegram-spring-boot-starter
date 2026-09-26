package io.github.baevkir.sessionbot.error;

import io.github.baevkir.sessionbot.CommandContext;
import lombok.Getter;

@Getter
public class BotAuthException extends RuntimeException {
    private final CommandContext context;

    public BotAuthException(CommandContext context, String message) {
        super(message);
        this.context = context;
    }
}
