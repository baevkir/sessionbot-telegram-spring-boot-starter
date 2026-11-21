package io.github.baevkir.sessionbot.handler;

import java.util.Arrays;
import java.util.List;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;

/**
 * What a {@link ReplyHandler} answers once it claims a text: the messages to send, in order, or
 * none at all for a claim that needs no answer.
 */
public final class Reply {

    private static final Reply SILENT = new Reply(List.of());

    private final List<PartialBotApiMethod<?>> messages;

    private Reply(List<PartialBotApiMethod<?>> messages) {
        this.messages = messages;
    }

    public static Reply of(PartialBotApiMethod<?>... messages) {
        return new Reply(List.copyOf(Arrays.asList(messages)));
    }

    public static Reply of(List<? extends PartialBotApiMethod<?>> messages) {
        return new Reply(List.copyOf(messages));
    }

    public static Reply silent() {
        return SILENT;
    }

    public List<PartialBotApiMethod<?>> messages() {
        return messages;
    }
}
