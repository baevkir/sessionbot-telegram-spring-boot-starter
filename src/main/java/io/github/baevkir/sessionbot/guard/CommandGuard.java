package io.github.baevkir.sessionbot.guard;

import reactor.core.publisher.Mono;

/**
 * Decides, per caller, whether a {@link Guarded} command may be seen and run. Implementations are
 * Spring beans, so they may inject whatever they need to decide. An error or an empty answer is
 * treated as a denial.
 */
@FunctionalInterface
public interface CommandGuard {
    Mono<Boolean> permits(GuardContext context);
}
