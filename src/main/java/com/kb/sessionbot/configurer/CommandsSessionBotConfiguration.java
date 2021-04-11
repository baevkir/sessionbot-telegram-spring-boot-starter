package com.kb.sessionbot.configurer;

import com.kb.sessionbot.commands.*;
import com.kb.sessionbot.commands.dispatcher.parameters.DateParameterRenderer;
import com.kb.sessionbot.commands.dispatcher.parameters.DefaultParameterRenderer;
import com.kb.sessionbot.commands.dispatcher.parameters.ParameterRenderer;
import com.kb.sessionbot.commands.errors.handler.BotCommandErrorHandler;
import com.kb.sessionbot.commands.errors.handler.ErrorHandler;
import com.kb.sessionbot.commands.errors.handler.ErrorHandlerFactory;
import com.kb.sessionbot.commands.dispatcher.annotations.BotCommand;
import com.kb.sessionbot.commands.dispatcher.DispatcherBotCommand;
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
            CommandSessionsHolder commandSessionsHolder,
            ErrorHandlerFactory errorHandler,
            CommandsSessionBotProperties properties) {

        CommandsSessionBot commandsSessionBot = new CommandsSessionBot(commandsFactory, commandSessionsHolder, errorHandler);
        commandsSessionBot.setBotUserName(properties.getBotUsername());
        commandsSessionBot.setToken(properties.getToken());
        return commandsSessionBot;
    }

    @Bean
    public CommandSessionsHolder commandsSessionCash() {
        return new CommandSessionsHolder();
    }

    @Bean
    public List<IBotCommand> reactiveBotCommand(ApplicationContext applicationContext, CommandSessionsHolder commandSessionsHolder) {
        return applicationContext.getBeansWithAnnotation(BotCommand.class)
                .values()
                .stream()
                .map(handler -> new DispatcherBotCommand(handler, commandSessionsHolder, applicationContext))
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
    public BotCommandErrorHandler botCommandErrorHandler(CommandSessionsHolder commandsSession) {
        return new BotCommandErrorHandler(commandsSession);
    }
}
