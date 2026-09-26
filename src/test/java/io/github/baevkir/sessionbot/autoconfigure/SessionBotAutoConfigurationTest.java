package io.github.baevkir.sessionbot.autoconfigure;

import io.github.baevkir.sessionbot.internal.CommandsSessionBot;
import io.github.baevkir.sessionbot.AuthInterceptor;
import io.github.baevkir.sessionbot.internal.CommandsFactory;
import io.github.baevkir.sessionbot.internal.HelpCommand;
import io.github.baevkir.sessionbot.render.ParameterRenderer;
import io.github.baevkir.sessionbot.error.BotAuthErrorHandler;
import io.github.baevkir.sessionbot.error.BotCommandErrorHandler;
import io.github.baevkir.sessionbot.internal.ErrorHandlerFactory;
import io.github.baevkir.sessionbot.guard.GuardDeniedHandler;
import io.github.baevkir.sessionbot.menu.CommandMenuService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class SessionBotAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SessionBotAutoConfiguration.class));

    // Required properties present plus mocked client + long-polling app so the context
    // builds without real network: registerBot never runs and the startup SetMyCommands
    // call hits the mocked client.
    private final ApplicationContextRunner activeRunner = runner
            .withPropertyValues(
                    "sessionbot.telegram.token=test-token",
                    "sessionbot.telegram.bot-username=test-bot")
            .withBean(TelegramClient.class, () -> Mockito.mock(TelegramClient.class))
            .withBean(TelegramBotsLongPollingApplication.class,
                    () -> Mockito.mock(TelegramBotsLongPollingApplication.class));

    @Test
    void backsOffWhenTelegramPropertiesAbsent() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(CommandsSessionBot.class);
        });
    }

    @Test
    void activatesWhenTelegramPropertiesPresent() {
        activeRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CommandsSessionBot.class);
            assertThat(context).hasSingleBean(HelpCommand.class);
            assertThat(context).hasSingleBean(CommandsFactory.class);
            assertThat(context).hasSingleBean(AuthInterceptor.class);
            assertThat(context).hasSingleBean(ErrorHandlerFactory.class);
            assertThat(context).hasSingleBean(BotCommandErrorHandler.class);
            assertThat(context).hasSingleBean(BotAuthErrorHandler.class);
            assertThat(context).hasBean("defaultParameterRenderer");
            assertThat(context).hasBean("textParameterRenderer");
            assertThat(context).hasBean("booleanParameterRenderer");
            assertThat(context).hasBean("dateParameterRenderer");
            assertThat(context).hasBean("timeParameterRenderer");
            assertThat(context.getBeansOfType(ParameterRenderer.class)).hasSize(5);
        });
    }

    @Test
    void allowsDownstreamToOverrideConditionalBeans() {
        AuthInterceptor custom = request -> Mono.just(false);
        activeRunner.withBean(AuthInterceptor.class, () -> custom).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AuthInterceptor.class);
            assertThat(context.getBean(AuthInterceptor.class)).isSameAs(custom);
        });
    }

    @Test
    void allowsDownstreamToOverrideDefaultParameterRenderer() {
        ParameterRenderer custom = request -> Mono.empty();
        activeRunner.withBean("defaultParameterRenderer", ParameterRenderer.class, () -> custom).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean("defaultParameterRenderer", ParameterRenderer.class)).isSameAs(custom);
        });
    }

    @Test
    void registersADefaultGuardDeniedHandler() {
        activeRunner.run(context -> assertThat(context).hasSingleBean(GuardDeniedHandler.class));
    }

    @Test
    void allowsDownstreamToOverrideTheGuardDeniedHandler() {
        GuardDeniedHandler custom = context -> Mono.empty();
        activeRunner.withBean(GuardDeniedHandler.class, () -> custom).run(context ->
            assertThat(context.getBean(GuardDeniedHandler.class)).isSameAs(custom));
    }

    @Test
    void registersACommandMenuService() {
        activeRunner.run(context -> assertThat(context).hasSingleBean(CommandMenuService.class));
    }
}