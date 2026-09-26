package com.kb.sessionbot.commands;

import com.kb.sessionbot.fixtures.Fixtures;
import com.kb.sessionbot.guard.CommandGuard;
import com.kb.sessionbot.model.CommandContext;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HelpCommandTest {

    private static final CommandGuard ALLOW = context -> Mono.just(true);
    private static final CommandGuard DENY = context -> Mono.just(false);

    @Test
    void listsUnguardedAndPermittedCommandsAndOmitsDeniedOnes() {
        var help = new HelpCommand(List.of(command("tasks"), command("admin", DENY), command("stats", ALLOW)),
            Fixtures.labels(Locale.ENGLISH));

        StepVerifier.create(Flux.from(help.process(Fixtures.contextFor("/help"))))
            .assertNext(result -> assertThat(((SendMessage) result).getText())
                .contains("/tasks", "/stats")
                .doesNotContain("/admin"))
            .verifyComplete();
    }

    @Test
    void aFailingGuardHidesOnlyItsCommandAndHelpStillAnswers() {
        CommandGuard failing = context -> Mono.error(new IllegalStateException("user store down"));
        var help = new HelpCommand(List.of(command("tasks"), command("admin", failing)), Fixtures.labels(Locale.ENGLISH));

        StepVerifier.create(Flux.from(help.process(Fixtures.contextFor("/help"))))
            .assertNext(result -> assertThat(((SendMessage) result).getText())
                .contains("/tasks")
                .doesNotContain("/admin"))
            .verifyComplete();
    }

    @Test
    void guardsSeeTheSenderEvenWhenHelpIsTheFallbackForPlainText() {
        AtomicReference<String> seen = new AtomicReference<>();
        CommandGuard capturing = context -> {
            seen.set(context.user() == null ? null : context.user().getUserName());
            return Mono.just(true);
        };
        var help = new HelpCommand(List.of(command("admin", capturing)), Fixtures.labels(Locale.ENGLISH));
        var plainText = CommandContext.empty()
            .addUpdate(Fixtures.wrap(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "hello")));

        StepVerifier.create(Flux.from(help.process(plainText))).expectNextCount(1).verifyComplete();
        assertThat(seen).hasValue("tester");
    }

    @Test
    void hiddenCommandsStayHidden() {
        IBotCommand hidden = command("secret");
        when(hidden.hidden()).thenReturn(true);
        var help = new HelpCommand(List.of(hidden), Fixtures.labels(Locale.ENGLISH));

        StepVerifier.create(Flux.from(help.process(Fixtures.contextFor("/help"))))
            .assertNext(result -> assertThat(((SendMessage) result).getText()).doesNotContain("/secret"))
            .verifyComplete();
    }

    @Test
    void descriptionsAreHtmlEscaped() {
        IBotCommand command = command("buy");
        when(command.getDescription(any())).thenReturn("Buy <item> & pay");
        var help = new HelpCommand(List.of(command), Fixtures.labels(Locale.ENGLISH));

        StepVerifier.create(Flux.from(help.process(Fixtures.contextFor("/help"))))
            .assertNext(result -> assertThat(((SendMessage) result).getText())
                .contains("Buy &lt;item&gt; &amp; pay")
                .doesNotContain("<item>"))
            .verifyComplete();
    }

    private static IBotCommand command(String identifier, CommandGuard... guards) {
        IBotCommand command = mock(IBotCommand.class);
        when(command.getCommandIdentifier()).thenReturn(identifier);
        when(command.getDescription(any())).thenReturn(identifier + " description");
        when(command.guards()).thenReturn(List.of(guards));
        return command;
    }
}
