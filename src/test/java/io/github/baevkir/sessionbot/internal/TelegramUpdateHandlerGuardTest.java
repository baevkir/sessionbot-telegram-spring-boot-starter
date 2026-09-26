package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.MessageExecutor;
import io.github.baevkir.sessionbot.AuthInterceptor;
import io.github.baevkir.sessionbot.fixtures.Fixtures;
import io.github.baevkir.sessionbot.guard.CommandGuard;
import io.github.baevkir.sessionbot.guard.GuardDeniedHandler;
import io.github.baevkir.sessionbot.UpdateWrapper;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramUpdateHandlerGuardTest {

    private static final AuthInterceptor ALLOW_ALL = context -> Mono.just(true);
    private static final CommandGuard ALLOW = context -> Mono.just(true);
    private static final CommandGuard DENY = context -> Mono.just(false);
    private static final GuardDeniedHandler DENIED = context -> Mono.just(send("denied"));

    @Test
    void aPermittedCommandIsProcessed() {
        RegisteredCommand admin = guardedCommand(ALLOW);

        StepVerifier.create(handler(admin, ALLOW_ALL, List.of()).handleUpdates(adminCommand()))
            .assertNext(result -> assertThat(text(result)).isEqualTo("admin"))
            .verifyComplete();
    }

    @Test
    void aDeniedCommandGoesToTheDeniedHandlerAndIsNeverProcessed() {
        RegisteredCommand admin = guardedCommand(DENY);

        StepVerifier.create(handler(admin, ALLOW_ALL, List.of()).handleUpdates(adminCommand()))
            .assertNext(result -> assertThat(text(result)).isEqualTo("denied"))
            .verifyComplete();
        verify(admin, never()).process(any());
    }

    @Test
    void thePermitListDoesNotBypassGuards() {
        RegisteredCommand admin = guardedCommand(DENY);
        AuthInterceptor denyAll = context -> Mono.just(false);

        StepVerifier.create(handler(admin, denyAll, List.of("admin")).handleUpdates(adminCommand()))
            .assertNext(result -> assertThat(text(result)).isEqualTo("denied"))
            .verifyComplete();
    }

    @Test
    void guardsSeeTheUserAfterTheInterceptorNormalizedIt() {
        AtomicReference<String> seenName = new AtomicReference<>();
        CommandGuard capturing = context -> {
            seenName.set(context.user().getUserName());
            return Mono.just(true);
        };
        AuthInterceptor renaming = context -> {
            context.getCommandUpdate().getFrom().setUserName("stored-name");
            return Mono.just(true);
        };

        StepVerifier.create(handler(guardedCommand(capturing), renaming, List.of()).handleUpdates(adminCommand()))
            .expectNextCount(1)
            .verifyComplete();
        assertThat(seenName).hasValue("stored-name");
    }

    @Test
    void aGuardIsReCheckedOnEveryUpdateOfTheCommand() {
        AtomicInteger calls = new AtomicInteger();
        CommandGuard firstCallOnly = context -> Mono.just(calls.incrementAndGet() == 1);
        RegisteredCommand admin = guardedCommand(firstCallOnly);
        var updates = Flux.just(
            Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/admin")),
            Fixtures.wrap(Fixtures.callbackUpdate(2, Fixtures.CHAT_ID, 100, "answer")));

        StepVerifier.create(handler(admin, ALLOW_ALL, List.of()).handleUpdates(updates))
            .assertNext(result -> assertThat(text(result)).isEqualTo("admin"))
            .assertNext(result -> assertThat(text(result)).isEqualTo("denied"))
            .verifyComplete();
        verify(admin, times(1)).process(any());
    }

    @Test
    void theLegacyConstructorAnswersADenialWithHelp() {
        RegisteredCommand admin = guardedCommand(DENY);
        RegisteredCommand help = mock(RegisteredCommand.class);
        doReturn(Mono.just(send("help"))).when(help).process(any());
        CommandsFactory commandsFactory = mock(CommandsFactory.class);
        when(commandsFactory.getCommand(any())).thenReturn(admin);
        when(commandsFactory.getHelpCommand()).thenReturn(help);
        var handler = new TelegramUpdateHandler(commandsFactory, ALLOW_ALL, mock(MessageExecutor.class),
            List.of(), List.of(), List.of(), List.of());

        StepVerifier.create(handler.handleUpdates(adminCommand()))
            .assertNext(result -> assertThat(text(result)).isEqualTo("help"))
            .verifyComplete();
    }

    private static TelegramUpdateHandler handler(RegisteredCommand command, AuthInterceptor auth, List<String> permitCommands) {
        CommandsFactory commandsFactory = mock(CommandsFactory.class);
        when(commandsFactory.getCommand(any())).thenReturn(command);
        return new TelegramUpdateHandler(commandsFactory, auth, mock(MessageExecutor.class),
            List.of(), List.of(), List.of(), permitCommands, DENIED);
    }

    private static RegisteredCommand guardedCommand(CommandGuard guard) {
        RegisteredCommand command = mock(RegisteredCommand.class);
        when(command.getCommandIdentifier()).thenReturn("admin");
        when(command.guards()).thenReturn(List.of(guard));
        doReturn(Flux.just(send("admin"))).when(command).process(any());
        return command;
    }

    private static Flux<UpdateWrapper> adminCommand() {
        return Flux.just(Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/admin")));
    }

    private static SendMessage send(String text) {
        return SendMessage.builder().chatId(String.valueOf(Fixtures.CHAT_ID)).text(text).build();
    }

    private static String text(Object result) {
        return ((SendMessage) result).getText();
    }
}
