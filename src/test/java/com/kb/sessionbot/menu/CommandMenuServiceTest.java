package com.kb.sessionbot.menu;

import com.kb.sessionbot.MessageExecutor;
import com.kb.sessionbot.commands.CommandsFactory;
import com.kb.sessionbot.commands.HelpCommand;
import com.kb.sessionbot.commands.IBotCommand;
import com.kb.sessionbot.fixtures.Fixtures;
import com.kb.sessionbot.guard.CommandGuard;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.commands.DeleteMyCommands;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeChat;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandMenuServiceTest {

    private static final String CHAT_ID = "4242";
    private static final CommandGuard ALLOW = context -> Mono.just(true);
    private static final CommandGuard DENY = context -> Mono.just(false);

    private final MessageExecutor executor = mock(MessageExecutor.class);

    @Test
    void aPermittedGuardedCommandSetsAChatScopedMenuWithEverythingTheCallerMayUse() {
        var service = service(command("tasks"), command("admin", ALLOW));

        StepVerifier.create(service.refresh(CHAT_ID, Fixtures.user("tester"))).verifyComplete();

        SetMyCommands sent = (SetMyCommands) sentMethod();
        assertThat(((BotCommandScopeChat) sent.getScope()).getChatId()).isEqualTo(CHAT_ID);
        assertThat(sent.getCommands()).extracting(BotCommand::getCommand).containsExactly("help", "tasks", "admin");
    }

    @Test
    void aCallerDeniedEveryGuardedCommandFallsBackToTheDefaultMenu() {
        var service = service(command("tasks"), command("admin", DENY));

        StepVerifier.create(service.refresh(CHAT_ID, Fixtures.user("tester"))).verifyComplete();

        assertDeletedChatScope();
    }

    @Test
    void aBotWithNoGuardedCommandsNeverCreatesAChatScope() {
        var service = service(command("tasks"));

        StepVerifier.create(service.refresh(CHAT_ID, Fixtures.user("tester"))).verifyComplete();

        assertDeletedChatScope();
    }

    @Test
    void resetDeletesTheChatScope() {
        StepVerifier.create(service(command("tasks")).reset(CHAT_ID)).verifyComplete();

        assertDeletedChatScope();
    }

    @Test
    void descriptionsAreLocalizedForTheCaller() {
        IBotCommand admin = command("admin", ALLOW);

        StepVerifier.create(service(admin).refresh(CHAT_ID, Fixtures.user("tester"))).verifyComplete();

        verify(admin).getDescription("tester");
    }

    private CommandMenuService service(IBotCommand... commands) {
        var help = new HelpCommand(List.of(commands), Fixtures.labels(Locale.ENGLISH));
        var factory = new CommandsFactory(help, List.of(commands));
        factory.start();
        return new CommandMenuService(factory, executor);
    }

    private PartialBotApiMethod<?> sentMethod() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<PartialBotApiMethod<?>> captor = (ArgumentCaptor) ArgumentCaptor.forClass(PartialBotApiMethod.class);
        verify(executor).execute(captor.capture());
        return captor.getValue();
    }

    private void assertDeletedChatScope() {
        DeleteMyCommands sent = (DeleteMyCommands) sentMethod();
        assertThat(((BotCommandScopeChat) sent.getScope()).getChatId()).isEqualTo(CHAT_ID);
    }

    private static IBotCommand command(String identifier, CommandGuard... guards) {
        IBotCommand command = mock(IBotCommand.class);
        when(command.getCommandIdentifier()).thenReturn(identifier);
        when(command.getDescription(any())).thenReturn(identifier + " description");
        when(command.guards()).thenReturn(List.of(guards));
        return command;
    }
}
