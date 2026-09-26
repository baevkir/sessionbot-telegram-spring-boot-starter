package io.github.baevkir.sessionbot.autoconfigure;

import io.github.baevkir.sessionbot.render.BooleanParameterRenderer;
import io.github.baevkir.sessionbot.render.CompositeParameterRenderer;
import io.github.baevkir.sessionbot.render.DateParameterRenderer;
import io.github.baevkir.sessionbot.render.ParameterRenderer;
import io.github.baevkir.sessionbot.render.TextParameterRenderer;
import io.github.baevkir.sessionbot.render.TimeParameterRenderer;
import io.github.baevkir.sessionbot.internal.CommandsSessionBot;
import io.github.baevkir.sessionbot.InboundUpdateBus;
import io.github.baevkir.sessionbot.MessageExecutor;
import io.github.baevkir.sessionbot.OutboundMessageBus;
import io.github.baevkir.sessionbot.internal.SinkInboundUpdateBus;
import io.github.baevkir.sessionbot.internal.SinkOutboundMessageBus;
import io.github.baevkir.sessionbot.internal.TelegramClientMessageExecutor;
import io.github.baevkir.sessionbot.internal.TelegramUpdateHandler;
import io.github.baevkir.sessionbot.AuthInterceptor;
import io.github.baevkir.sessionbot.internal.CommandsFactory;
import io.github.baevkir.sessionbot.internal.HelpCommand;
import io.github.baevkir.sessionbot.internal.RegisteredCommand;
import io.github.baevkir.sessionbot.internal.DispatcherBotCommand;
import io.github.baevkir.sessionbot.annotation.BotCommand;
import io.github.baevkir.sessionbot.handler.ContactHandler;
import io.github.baevkir.sessionbot.handler.DocumentHandler;
import io.github.baevkir.sessionbot.error.BotAuthErrorHandler;
import io.github.baevkir.sessionbot.error.BotCommandErrorHandler;
import io.github.baevkir.sessionbot.error.ErrorHandler;
import io.github.baevkir.sessionbot.internal.ErrorHandlerFactory;
import io.github.baevkir.sessionbot.guard.GuardDeniedHandler;
import io.github.baevkir.sessionbot.internal.HelpGuardDeniedHandler;
import io.github.baevkir.sessionbot.i18n.BotLabels;
import io.github.baevkir.sessionbot.i18n.ConfiguredLocaleProvider;
import io.github.baevkir.sessionbot.i18n.LocaleProvider;
import io.github.baevkir.sessionbot.menu.CommandMenuService;
import io.github.baevkir.sessionbot.handler.TextHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Auto-configuration for the session bot. Activates when {@code sessionbot.telegram.token}
 * and {@code bot-username} are set, wiring the bot, its {@link TelegramClient} and
 * long-polling registration, command dispatch, parameter renderers, auth and error
 * handling. Most beans are {@code @ConditionalOnMissingBean} so a consuming app can override
 * any of them by declaring its own.
 */
@AutoConfiguration
@ConditionalOnProperty(value = {"token", "bot-username"}, prefix = "sessionbot.telegram")
@EnableConfigurationProperties(SessionBotProperties.class)
public class SessionBotAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TelegramClient telegramClient(SessionBotProperties properties) {
        return new OkHttpTelegramClient(properties.getToken());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public TelegramBotsLongPollingApplication telegramBotsApplication(
            CommandsSessionBot bot, SessionBotProperties properties) throws TelegramApiException {
        var application = new TelegramBotsLongPollingApplication();
        application.registerBot(properties.getToken(), bot);
        return application;
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageExecutor messageExecutor(TelegramClient telegramClient, ErrorHandlerFactory errorHandler) {
        return new TelegramClientMessageExecutor(telegramClient, errorHandler);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboundMessageBus outboundMessageBus() {
        return new SinkOutboundMessageBus();
    }

    @Bean
    @ConditionalOnMissingBean
    public GuardDeniedHandler guardDeniedHandler(HelpCommand helpCommand) {
        return new HelpGuardDeniedHandler(helpCommand);
    }

    @Bean
    @ConditionalOnMissingBean
    public CommandMenuService commandMenuService(CommandsFactory commandsFactory, MessageExecutor messageExecutor) {
        return new CommandMenuService(commandsFactory, messageExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    public TelegramUpdateHandler telegramUpdateHandler(
            CommandsFactory commandsFactory,
            AuthInterceptor authInterceptor,
            MessageExecutor messageExecutor,
            ObjectProvider<DocumentHandler> documentHandlers,
            ObjectProvider<ContactHandler> contactHandlers,
            ObjectProvider<TextHandler> textHandlers,
            SessionBotProperties properties,
            GuardDeniedHandler guardDeniedHandler) {
        return new TelegramUpdateHandler(commandsFactory, authInterceptor, messageExecutor,
            documentHandlers.orderedStream().toList(),
            contactHandlers.orderedStream().toList(),
            textHandlers.orderedStream().toList(),
            properties.getPermitCommands(),
            guardDeniedHandler);
    }

    @Bean
    @ConditionalOnMissingBean
    public InboundUpdateBus inboundUpdateBus(SessionBotProperties properties) {
        return new SinkInboundUpdateBus(properties.getChatIdleTtl());
    }

    @Bean
    public CommandsSessionBot bot(
            CommandsFactory commandsFactory,
            ErrorHandlerFactory errorHandler,
            MessageExecutor messageExecutor,
            OutboundMessageBus outboundMessageBus,
            TelegramUpdateHandler telegramUpdateHandler,
            InboundUpdateBus inboundUpdateBus,
            SessionBotProperties properties) {
        return new CommandsSessionBot(commandsFactory, errorHandler, messageExecutor,
            outboundMessageBus, telegramUpdateHandler, inboundUpdateBus, properties.getMaxConcurrentChats());
    }


    @Bean
    public List<RegisteredCommand> reactiveBotCommand(ApplicationContext applicationContext) {
        return applicationContext.getBeansWithAnnotation(BotCommand.class)
                .values()
                .stream()
                .map(handler -> new DispatcherBotCommand(handler, applicationContext))
                .collect(Collectors.toList());
    }

    @Bean
    public HelpCommand helpCommand(List<RegisteredCommand> botCommands, BotLabels botLabels) {
        return new HelpCommand(botCommands, botLabels);
    }

    @Bean
    public CommandsFactory commandsFactory(HelpCommand helpCommand) {
        return new CommandsFactory(helpCommand, helpCommand.getBotCommands());
    }

    @Bean
    @ConditionalOnMissingBean(name = "defaultParameterRenderer")
    public ParameterRenderer defaultParameterRenderer(ParameterRenderer textParameterRenderer, ParameterRenderer dateParameterRenderer,
                                                      ParameterRenderer booleanParameterRenderer, ParameterRenderer timeParameterRenderer) {
        return new CompositeParameterRenderer(textParameterRenderer, dateParameterRenderer, booleanParameterRenderer, timeParameterRenderer);
    }

    @Bean
    @ConditionalOnMissingBean(name = "textParameterRenderer")
    public ParameterRenderer textParameterRenderer(BotLabels botLabels) {
        return new TextParameterRenderer(botLabels);
    }

    @Bean
    @ConditionalOnMissingBean(name = "booleanParameterRenderer")
    public ParameterRenderer booleanParameterRenderer(BotLabels botLabels) {
        return new BooleanParameterRenderer(botLabels);
    }

    @Bean
    @ConditionalOnMissingBean(name = "dateParameterRenderer")
    public ParameterRenderer dateParameterRenderer(BotLabels botLabels) {
        return new DateParameterRenderer(botLabels);
    }

    @Bean
    @ConditionalOnMissingBean(name = "timeParameterRenderer")
    public ParameterRenderer timeParameterRenderer(BotLabels botLabels) {
        return new TimeParameterRenderer(botLabels);
    }

    @Bean
    public ErrorHandlerFactory errorHandlerFactory(List<ErrorHandler<?>> errorHandlers) {
        return new ErrorHandlerFactory(errorHandlers);
    }

    @Bean
    @ConditionalOnMissingBean(name = "botCommandErrorHandler")
    public BotCommandErrorHandler botCommandErrorHandler(BotLabels botLabels) {
        return new BotCommandErrorHandler(botLabels);
    }

    @Bean
    @ConditionalOnMissingBean(name = "botAuthErrorHandler")
    public BotAuthErrorHandler botAuthErrorHandler(BotLabels botLabels) {
        return new BotAuthErrorHandler(botLabels);
    }


    @Bean
    @ConditionalOnMissingBean
    public AuthInterceptor authInterceptor() {
        return request -> Mono.just(true);
    }

    @Bean
    @ConditionalOnMissingBean(name = "sessionbotLabelsMessageSource")
    public MessageSource sessionbotLabelsMessageSource(
            @Qualifier("messageSource") ObjectProvider<MessageSource> appMessageSource) {
        var ms = new ResourceBundleMessageSource();
        ms.setBasenames("sessionbot-labels-override", "sessionbot-labels");
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false);
        ms.setUseCodeAsDefaultMessage(false);
        appMessageSource.ifAvailable(ms::setParentMessageSource);
        return ms;
    }

    @Bean
    @ConditionalOnMissingBean
    public LocaleProvider localeProvider(SessionBotProperties properties) {
        return new ConfiguredLocaleProvider(Locale.forLanguageTag(properties.getLanguage()));
    }

    @Bean
    @ConditionalOnMissingBean
    public BotLabels botLabels(
            @Qualifier("sessionbotLabelsMessageSource") MessageSource sessionbotLabelsMessageSource,
            LocaleProvider localeProvider) {
        return new BotLabels(sessionbotLabelsMessageSource, localeProvider);
    }
}
