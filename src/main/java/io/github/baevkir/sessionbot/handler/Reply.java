package io.github.baevkir.sessionbot.handler;

import java.util.Arrays;
import java.util.List;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;

/**
 * What a {@link ReplyHandler} answers once it claims a text: the messages to send, in order, or
 * none at all for a claim that needs no answer. An empty reply still takes the text: nothing is
 * sent, and no later handler sees it.
 */
public final class Reply {

    private static final Reply SILENT = new Reply(List.of());

    private final List<PartialBotApiMethod<?>> messages;

    private Reply(List<PartialBotApiMethod<?>> messages) {
        this.messages = messages;
    }

    public static Reply of(PartialBotApiMethod<?>... messages) {
        return of(Arrays.asList(messages));
    }

    public static Reply of(List<? extends PartialBotApiMethod<?>> messages) {
        List<PartialBotApiMethod<?>> copy = List.copyOf(messages);
        return copy.isEmpty() ? SILENT : new Reply(copy);
    }

    public static Reply silent() {
        return SILENT;
    }

    public List<PartialBotApiMethod<?>> messages() {
        return messages;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Reply reply && messages.equals(reply.messages);
    }

    @Override
    public int hashCode() {
        return messages.hashCode();
    }

    @Override
    public String toString() {
        return "Reply" + messages;
    }
}
