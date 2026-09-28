package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.guard.CommandGuard;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.objects.User;

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
     * @param user the caller to localize for; {@code null} for the bot-wide language (the default
     *             command menu)
     * @return the description as String
     */
    String getDescription(User user);

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
