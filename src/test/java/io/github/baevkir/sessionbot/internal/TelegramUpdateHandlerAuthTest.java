package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.MessageExecutor;
import io.github.baevkir.sessionbot.AuthInterceptor;
import io.github.baevkir.sessionbot.fixtures.Fixtures;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelegramUpdateHandlerAuthTest {

    private static final String DENY_ALL_USER = "tester";

    private TelegramUpdateHandler handlerWith(AuthInterceptor authInterceptor,
                                              RegisteredCommand command,
                                              List<String> permitCommands) {
        CommandsFactory commandsFactory = mock(CommandsFactory.class);
        when(commandsFactory.getCommand(any())).thenReturn(command);
        MessageExecutor messageExecutor = mock(MessageExecutor.class);
        return new TelegramUpdateHandler(commandsFactory, authInterceptor, messageExecutor,
            List.of(), List.of(), List.of(), permitCommands);
    }

    @Test
    void permitListedCommandRunsWithoutTheAuthInterceptor() {
        AuthInterceptor denyAll = context -> Mono.just(false);
        RegisteredCommand startCommand = mock(RegisteredCommand.class);
        doReturn(Flux.just(SendMessage.builder().chatId("1").text("started").build()))
            .when(startCommand).process(any());

        TelegramUpdateHandler handler = handlerWith(denyAll, startCommand, List.of("start"));
        var updates = Flux.just(Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/start")));

        StepVerifier.create(handler.handleUpdates(updates))
            .assertNext(result -> assertThat(((SendMessage) result).getText()).isEqualTo("started"))
            .verifyComplete();
    }

    @Test
    void commandOutsideThePermitListStillGoesThroughTheAuthInterceptor() {
        AuthInterceptor denyAll = context -> Mono.just(false);
        RegisteredCommand tasksCommand = mock(RegisteredCommand.class);

        TelegramUpdateHandler handler = handlerWith(denyAll, tasksCommand, List.of("start"));
        var updates = Flux.just(Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/tasks")));

        StepVerifier.create(handler.handleUpdates(updates))
            .expectErrorMessage("User " + DENY_ALL_USER + " is unauthorized to use bot.")
            .verify();
    }

    @Test
    void nullPermitListStillDispatchesThroughTheAuthInterceptor() {
        AuthInterceptor denyAll = context -> Mono.just(false);
        RegisteredCommand tasksCommand = mock(RegisteredCommand.class);

        TelegramUpdateHandler handler = handlerWith(denyAll, tasksCommand, null);
        var updates = Flux.just(Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/tasks")));

        StepVerifier.create(handler.handleUpdates(updates))
            .expectErrorMessage("User " + DENY_ALL_USER + " is unauthorized to use bot.")
            .verify();
    }
}
