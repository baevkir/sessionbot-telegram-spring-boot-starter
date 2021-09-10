package com.kb.sessionbot.config;

import com.kb.sessionbot.CommandsSessionBot;
import com.kb.sessionbot.auth.AuthInterceptor;
import com.kb.sessionbot.commands.CommandsFactory;
import com.kb.sessionbot.commands.HelpCommand;
import com.kb.sessionbot.commands.IBotCommand;
import com.kb.sessionbot.commands.dispatcher.DispatcherBotCommand;
import com.kb.sessionbot.commands.dispatcher.annotations.BotCommand;
import com.kb.sessionbot.commands.dispatcher.parameters.DateParameterRenderer;
import com.kb.sessionbot.commands.dispatcher.parameters.DefaultParameterRenderer;
import com.kb.sessionbot.commands.dispatcher.parameters.ParameterRenderer;
import com.kb.sessionbot.errors.handler.BotAuthErrorHandler;
import com.kb.sessionbot.errors.handler.BotCommandErrorHandler;
import com.kb.sessionbot.errors.handler.ErrorHandler;
import com.kb.sessionbot.errors.handler.ErrorHandlerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.List;
import java.util.stream.Collectors;

@Configuration
@ConditionalOnProperty(value = {"token", "bot-username"}, prefix = "sessionbot.telegram")
@EnableConfigurationProperties(CommandsSessionBotProperties.class)
public class CommandsSessionBotConfiguration {

    @Bean
    public TelegramBotsApi telegramBotsApi(CommandsSessionBot bot) throws TelegramApiException {
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        telegramBotsApi.registerBot(bot);
        return telegramBotsApi;
    }

    @Bean
    public CommandsSessionBot bot(
            CommandsFactory commandsFactory,
            ErrorHandlerFactory errorHandler,
            AuthInterceptor authInterceptor,
            CommandsSessionBotProperties properties) {
        return new CommandsSessionBot(commandsFactory, authInterceptor, errorHandler, properties);
    }


    @Bean
    public List<IBotCommand> reactiveBotCommand(ApplicationContext applicationContext) {
        return applicationContext.getBeansWithAnnotation(BotCommand.class)
                .values()
                .stream()
                .map(handler -> new DispatcherBotCommand(handler, applicationContext))
                .collect(Collectors.toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public HelpCommand helpCommand(List<IBotCommand> botCommands) {
        return new HelpCommand(botCommands);
    }

    @Bean
    @ConditionalOnMissingBean
    public CommandsFactory commandsFactory(HelpCommand helpCommand) {
        return new CommandsFactory(helpCommand, helpCommand.getBotCommands());
    }

    @Bean
    @ConditionalOnMissingBean(name = "defaultParameterRenderer")
    public ParameterRenderer defaultParameterRenderer() {
       return new DefaultParameterRenderer();
    }

    @Bean
    @ConditionalOnMissingBean(name = "dateParameterRenderer")
    public ParameterRenderer dateParameterRenderer() {
        return new DateParameterRenderer();
    }

    @Bean
    public ErrorHandlerFactory errorHandlerFactory(List<ErrorHandler<?>> errorHandlers) {
        return new ErrorHandlerFactory(errorHandlers);
    }

    @Bean
    @ConditionalOnMissingBean(name = "botCommandErrorHandler")
    public BotCommandErrorHandler botCommandErrorHandler() {
        return new BotCommandErrorHandler();
    }

    @Bean
    @ConditionalOnMissingBean(name = "botAuthErrorHandler")
    public BotAuthErrorHandler botAuthErrorHandler() {
        return new BotAuthErrorHandler();
    }


    @Bean
    @ConditionalOnMissingBean
    public AuthInterceptor authInterceptor() {
        return request -> true;
    }
}
