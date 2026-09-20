package com.kb.sessionbot;

import com.kb.sessionbot.auth.AuthInterceptor;
import com.kb.sessionbot.commands.CommandsFactory;
import com.kb.sessionbot.commands.IBotCommand;
import com.kb.sessionbot.model.UpdateWrapper;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
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

    private static final String DENY_ALL_USER = "stranger";

    @Test
    void permitListedCommandRunsWithoutTheAuthInterceptor() {
        AuthInterceptor denyAll = context -> Mono.just(false);
        IBotCommand startCommand = mock(IBotCommand.class);
        doReturn(Flux.just(SendMessage.builder().chatId("1").text("started").build()))
            .when(startCommand).process(any());

        TelegramUpdateHandler handler = handlerWith(denyAll, startCommand, List.of("start"));

        StepVerifier.create(handler.handleUpdates(Flux.just(commandUpdate("/start"))))
            .assertNext(result -> assertThat(((SendMessage) result).getText()).isEqualTo("started"))
            .verifyComplete();
    }

    @Test
    void commandOutsideThePermitListStillGoesThroughTheAuthInterceptor() {
        AuthInterceptor denyAll = context -> Mono.just(false);
        IBotCommand tasksCommand = mock(IBotCommand.class);

        TelegramUpdateHandler handler = handlerWith(denyAll, tasksCommand, List.of("start"));

        StepVerifier.create(handler.handleUpdates(Flux.just(commandUpdate("/tasks"))))
            .expectErrorMessage("User " + DENY_ALL_USER + " is unauthorized to use bot.")
            .verify();
    }

    private TelegramUpdateHandler handlerWith(AuthInterceptor authInterceptor,
                                              IBotCommand command,
                                              List<String> permitCommands) {
        CommandsFactory commandsFactory = mock(CommandsFactory.class);
        when(commandsFactory.getCommand(any())).thenReturn(command);
        MessageExecutor messageExecutor = mock(MessageExecutor.class);
        return new TelegramUpdateHandler(commandsFactory, authInterceptor, messageExecutor,
            List.of(), List.of(), List.of(), permitCommands);
    }

    private UpdateWrapper commandUpdate(String text) {
        User from = User.builder()
            .id(42L)
            .userName(DENY_ALL_USER)
            .firstName("Test")
            .isBot(false)
            .build();
        Message message = new Message();
        message.setMessageId(1);
        message.setText(text);
        message.setFrom(from);
        message.setChat(new org.telegram.telegrambots.meta.api.objects.chat.Chat(1L, "private"));
        Update update = new Update();
        update.setMessage(message);
        return UpdateWrapper.wrap(update);
    }
}
