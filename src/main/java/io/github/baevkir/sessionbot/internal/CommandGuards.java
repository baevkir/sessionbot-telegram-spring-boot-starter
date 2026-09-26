package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.guard.GuardContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The single place a command's guards are evaluated, shared by dispatch, {@code /help} and the command
 * menu so the three can never disagree. Guards run in order and stop at the first denial.
 */
@Slf4j
public final class CommandGuards {

    private CommandGuards() {
    }

    public static Mono<Boolean> permits(RegisteredCommand command, GuardContext context) {
        return Flux.fromIterable(command.guards())
            .concatMap(guard -> Mono.defer(() -> guard.permits(context))
                .defaultIfEmpty(false)
                .onErrorResume(error -> {
                    log.warn("Guard {} failed for command '{}'; denying", guard.getClass().getSimpleName(), context.command(), error);
                    return Mono.just(false);
                }))
            .takeUntil(permitted -> !permitted)
            .all(Boolean::booleanValue);
    }
}
