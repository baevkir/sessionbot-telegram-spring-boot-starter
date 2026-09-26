package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.guard.CommandGuard;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;

import java.util.List;


public interface RegisteredCommand {
    /**
     * Get the identifier of this command
     *
     * @return the identifier
     */
    String getCommandIdentifier();

    /**
     * Get the description of this command, localized for the given user.
     *
     * @param userName the Telegram user name to resolve the language for; {@code null} for the
     *                 bot-wide/configured language (e.g. the startup command list, which has no user)
     * @return the description as String
     */
    String getDescription(String userName);

    /**
     * @return the true if bot command should not show in help
     */
    default boolean hidden() {
        return false;
    }

    /**
     * @return the guards that must all permit a caller before this command is shown to or run for them;
     *         empty for an unrestricted command
     */
    default List<CommandGuard> guards() {
        return List.of();
    }

    /**
     * Runs one dispatch step of this command, advancing the conversation.
     *
     * @param conversation the chat's conversation state
     * @return the messages to send
     */
    Publisher<? extends PartialBotApiMethod<?>> process(ConversationState conversation);
}
