package com.kb.sessionbot;

import com.kb.sessionbot.auth.AuthInterceptor;
import com.kb.sessionbot.commands.CommandsFactory;
import com.kb.sessionbot.commands.IBotCommand;
import com.kb.sessionbot.fixtures.Fixtures;
import com.kb.sessionbot.guard.CommandGuard;
import com.kb.sessionbot.guard.GuardDeniedHandler;
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
        IBotCommand admin = guardedCommand(ALLOW);

        StepVerifier.create(handler(admin, ALLOW_ALL, List.of()).handleUpdates(adminCommand()))
            .assertNext(result -> assertThat(text(result)).isEqualTo("admin"))
            .verifyComplete();
    }

    @Test
    void aDeniedCommandGoesToTheDeniedHandlerAndIsNeverProcessed() {
        IBotCommand admin = guardedCommand(DENY);

        StepVerifier.create(handler(admin, ALLOW_ALL, List.of()).handleUpdates(adminCommand()))
            .assertNext(result -> assertThat(text(result)).isEqualTo("denied"))
            .verifyComplete();
        verify(admin, never()).process(any());
    }

    @Test
    void thePermitListDoesNotBypassGuards() {
        IBotCommand admin = guardedCommand(DENY);
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
        IBotCommand admin = guardedCommand(firstCallOnly);
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
        IBotCommand admin = guardedCommand(DENY);
        IBotCommand help = mock(IBotCommand.class);
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

    private static TelegramUpdateHandler handler(IBotCommand command, AuthInterceptor auth, List<String> permitCommands) {
        CommandsFactory commandsFactory = mock(CommandsFactory.class);
        when(commandsFactory.getCommand(any())).thenReturn(command);
        return new TelegramUpdateHandler(commandsFactory, auth, mock(MessageExecutor.class),
            List.of(), List.of(), List.of(), permitCommands, DENIED);
    }

    private static IBotCommand guardedCommand(CommandGuard guard) {
        IBotCommand command = mock(IBotCommand.class);
        when(command.getCommandIdentifier()).thenReturn("admin");
        when(command.guards()).thenReturn(List.of(guard));
        doReturn(Flux.just(send("admin"))).when(command).process(any());
        return command;
    }

    private static Flux<com.kb.sessionbot.model.UpdateWrapper> adminCommand() {
        return Flux.just(Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/admin")));
    }

    private static SendMessage send(String text) {
        return SendMessage.builder().chatId(String.valueOf(Fixtures.CHAT_ID)).text(text).build();
    }

    private static String text(Object result) {
        return ((SendMessage) result).getText();
    }
}
