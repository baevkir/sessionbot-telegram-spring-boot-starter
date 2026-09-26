package com.kb.sessionbot.commands.dispatcher;

import com.kb.sessionbot.commands.dispatcher.annotations.BotCommand;
import com.kb.sessionbot.commands.dispatcher.annotations.CommandMethod;
import com.kb.sessionbot.guard.CommandGuard;
import com.kb.sessionbot.guard.GuardContext;
import com.kb.sessionbot.guard.Guarded;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DispatcherBotCommandGuardsTest {

    public static class AllowGuard implements CommandGuard {
        @Override
        public Mono<Boolean> permits(GuardContext context) {
            return Mono.just(true);
        }
    }

    @Guarded(AllowGuard.class)
    @BotCommand("guarded")
    public static class GuardedCommand {
        @CommandMethod
        public SendMessage run() {
            return SendMessage.builder().chatId("1").text("ran").build();
        }
    }

    @BotCommand("open")
    public static class OpenCommand {
        @CommandMethod
        public SendMessage run() {
            return SendMessage.builder().chatId("1").text("ran").build();
        }
    }

    @Test
    void guardsAreResolvedAsBeans() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AllowGuard.class);
            context.refresh();

            var command = new DispatcherBotCommand(new GuardedCommand(), context);

            assertThat(command.guards()).singleElement().isSameAs(context.getBean(AllowGuard.class));
        }
    }

    @Test
    void anUnguardedCommandHasNoGuards() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.refresh();

            assertThat(new DispatcherBotCommand(new OpenCommand(), context).guards()).isEmpty();
        }
    }

    @Test
    void aMissingGuardBeanFailsNamingTheCommandAndTheGuard() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.refresh();

            assertThatThrownBy(() -> new DispatcherBotCommand(new GuardedCommand(), context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'guarded'")
                .hasMessageContaining(AllowGuard.class.getName());
        }
    }
}
