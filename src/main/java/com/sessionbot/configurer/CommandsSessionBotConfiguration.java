package com.sessionbot.configurer;

import com.sessionbot.commands.*;
import com.sessionbot.commands.annotations.BotCommand;
import com.sessionbot.errors.handler.BotCommandErrorHandler;
import com.sessionbot.errors.handler.ChatValidationErrorHandler;
import com.sessionbot.errors.handler.DateValidationErrorHandler;
import com.sessionbot.errors.handler.ErrorHandler;
import com.sessionbot.errors.handler.ErrorHandlerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.util.List;
import java.util.stream.Collectors;

@Configuration
@ConditionalOnProperty(value = {"token", "bot-username"}, prefix = "sessionbot.telegram")
@EnableConfigurationProperties(CommandsSessionBotProperties.class)
public class CommandsSessionBotConfiguration {

    static {
        ApiContextInitializer.init();
    }

    @Bean
    public TelegramBotsApi telegramBotsApi(CommandsSessionBot bot) throws TelegramApiRequestException {
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi();
        telegramBotsApi.registerBot(bot);
        return telegramBotsApi;
    }

    @Bean
    public CommandsSessionBot bot(
            CommandsFactory commandsFactory,
            CommandsSessionCash commandsSessionCash,
            ErrorHandlerFactory errorHandler,
            CommandsSessionBotProperties properties) {

        CommandsSessionBot commandsSessionBot = new CommandsSessionBot(commandsFactory, commandsSessionCash, errorHandler);
        commandsSessionBot.setBotUserName(properties.getBotUsername());
        commandsSessionBot.setToken(properties.getToken());
        return commandsSessionBot;
    }

    @Bean
    public CommandsSessionCash commandsSessionCash() {
        return new CommandsSessionCash();
    }

    @Bean
    public List<IBotCommand> reactiveBotCommand(ApplicationContext applicationContext, CommandsSessionCash commandsSessionCash) {
        return applicationContext.getBeansWithAnnotation(BotCommand.class)
                .values()
                .stream()
                .map(handler -> new ReactiveBotCommand(handler, commandsSessionCash))
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
    public ErrorHandlerFactory errorHandlerFactory(List<ErrorHandler<?>> errorHandlers) {
        return new ErrorHandlerFactory(errorHandlers);
    }

    @Bean
    @ConditionalOnMissingBean
    public BotCommandErrorHandler botCommandErrorHandler(CommandsSessionCash commandsSession) {
        return new BotCommandErrorHandler(commandsSession);
    }

    @Bean
    public ChatValidationErrorHandler chatValidationErrorHandler() {
        return new ChatValidationErrorHandler();
    }

    @Bean
    public DateValidationErrorHandler dateValidationErrorHandler() {
        return new DateValidationErrorHandler();
    }
}
