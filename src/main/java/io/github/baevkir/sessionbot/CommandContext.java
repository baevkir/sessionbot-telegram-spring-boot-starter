package io.github.baevkir.sessionbot;

import io.github.baevkir.sessionbot.internal.ConversationState;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage;

import java.util.List;
import java.util.Optional;

/**
 * Read-only view of one chat's conversation with the bot: the update that opened it — a command, or a
 * bare text, document or contact outside any command — the answers collected so far and the latest
 * update. Commands, guards, handlers and renderers receive it; only the library advances it.
 */
public interface CommandContext {

    /** A context for a single update: a command opens a command context, anything else a bare one. For tests and out-of-band use. */
    static CommandContext of(Update update) {
        var wrapper = UpdateWrapper.wrap(update);
        return wrapper.isCommand() ? ConversationState.forCommand(wrapper) : ConversationState.forBareUpdate(wrapper);
    }

    String getChatId();

    /** The sender of the opening update, as the {@link AuthInterceptor} left it; {@code null} when it has none. */
    User getUser();

    /** The command name, or {@code null} for a bare update. */
    String getCommand();

    /** The answers collected so far, including those the latest update carries; unmodifiable. */
    List<String> getAnswers();

    /** The update that opened the context: the command, or the bare update itself. */
    UpdateWrapper getCommandUpdate();

    /** The latest update of the conversation. */
    Optional<UpdateWrapper> getCurrentUpdate();

    /** The message whose inline button was tapped: the latest update's, else the opening update's. */
    Optional<MaybeInaccessibleMessage> getCallbackMessage();

    DynamicParameters getDynamicParams();
}
