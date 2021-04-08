package com.sessionbot.configurer;

import com.sessionbot.commands.CommandsFactory;
import com.sessionbot.commands.CommandsSessionBot;
import com.sessionbot.commands.CommandsSessionCash;
import com.sessionbot.commands.BotCommand;
import com.sessionbot.commands.HelpCommand;
import com.sessionbot.errors.handler.BotCommandErrorHandler;
import com.sessionbot.errors.handler.ChatValidationErrorHandler;
import com.sessionbot.errors.handler.DateValidationErrorHandler;
import com.sessionbot.errors.handler.ErrorHandler;
import com.sessionbot.errors.handler.ErrorHandlerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.util.List;

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
    @ConditionalOnMissingBean
    public CommandsFactory commandsFactory(HelpCommand helpCommand, List<BotCommand> botCommands) {
        return new CommandsFactory(helpCommand, botCommands);
    }

    @Bean
    @ConditionalOnMissingBean
    public HelpCommand helpCommand(List<BotCommand> botCommands) {
        return new HelpCommand(botCommands);
    }

    @Bean
    public CommandsSessionCash commandsSessionCash() {
        return new CommandsSessionCash();
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
