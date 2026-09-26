package io.github.baevkir.sessionbot;

import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;

import java.io.Serializable;

/**
 * Executes a single outbound Telegram method and returns its API result (e.g. the sent
 * {@link org.telegram.telegrambots.meta.api.objects.message.Message}). The likeliest
 * custom-override point for a consuming app, hence an interface.
 *
 * <p>Contract: a failed call never throws. The implementation reports the failure (the default logs
 * it) and returns {@code null}, so one rejected message cannot break a chat's conversation.
 */
public interface MessageExecutor {
    <T extends Serializable> T execute(PartialBotApiMethod<T> message);
}