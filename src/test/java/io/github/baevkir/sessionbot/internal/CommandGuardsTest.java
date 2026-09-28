package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.guard.CommandGuard;
import io.github.baevkir.sessionbot.guard.GuardContext;
import io.github.baevkir.sessionbot.fixtures.Fixtures;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandGuardsTest {

    private static final GuardContext CONTEXT =
        new GuardContext(Fixtures.user("tester"), "4242", "private", "admin");
    private static final CommandGuard ALLOW = context -> Mono.just(true);
    private static final CommandGuard DENY = context -> Mono.just(false);

    @Test
    void aCommandWithoutGuardsIsPermitted() {
        StepVerifier.create(CommandGuards.permits(commandWith(), CONTEXT)).expectNext(true).verifyComplete();
    }

    @Test
    void everyGuardMustPermit() {
        StepVerifier.create(CommandGuards.permits(commandWith(ALLOW, DENY), CONTEXT)).expectNext(false).verifyComplete();
        StepVerifier.create(CommandGuards.permits(commandWith(ALLOW, ALLOW), CONTEXT)).expectNext(true).verifyComplete();
    }

    @Test
    void theFirstDenialShortCircuits() {
        AtomicBoolean secondCalled = new AtomicBoolean();
        CommandGuard spy = context -> {
            secondCalled.set(true);
            return Mono.just(true);
        };

        StepVerifier.create(CommandGuards.permits(commandWith(DENY, spy), CONTEXT)).expectNext(false).verifyComplete();
        assertThat(secondCalled).isFalse();
    }

    @Test
    void aGuardThatErrorsDenies() {
        CommandGuard failing = context -> Mono.error(new IllegalStateException("user store down"));
        StepVerifier.create(CommandGuards.permits(commandWith(failing), CONTEXT)).expectNext(false).verifyComplete();
    }

    @Test
    void aGuardThatThrowsSynchronouslyDenies() {
        CommandGuard throwing = context -> {
            throw new IllegalStateException("broken guard");
        };
        StepVerifier.create(CommandGuards.permits(commandWith(throwing), CONTEXT)).expectNext(false).verifyComplete();
    }

    @Test
    void anEmptyGuardAnswerDenies() {
        CommandGuard silent = context -> Mono.empty();
        StepVerifier.create(CommandGuards.permits(commandWith(silent), CONTEXT)).expectNext(false).verifyComplete();
    }

    private static RegisteredCommand commandWith(CommandGuard... guards) {
        RegisteredCommand command = mock(RegisteredCommand.class);
        when(command.guards()).thenReturn(List.of(guards));
        when(command.getCommandIdentifier()).thenReturn("admin");
        return command;
    }
}
