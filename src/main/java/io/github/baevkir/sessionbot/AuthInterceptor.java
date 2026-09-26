package io.github.baevkir.sessionbot;

import reactor.core.publisher.Mono;

/**
 * Gates every command before it runs. Returning {@code false} rejects the command with a
 * {@code BotAuthException}. The default bean allows all; a consuming app overrides it.
 */
public interface AuthInterceptor {
    Mono<Boolean> intercept(CommandContext context);
}
