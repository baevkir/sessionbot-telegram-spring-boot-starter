package com.sessionbot.telegram.configurer;

import com.sessionbot.telegram.CommandsFactory;
import com.sessionbot.telegram.CommandsSessionBot;
import com.sessionbot.telegram.errors.handler.ErrorHandlerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

@Configuration
@ConditionalOnClass({CommandsSessionBot.class, CommandsFactory.class, ErrorHandlerFactory.class})
@ConditionalOnProperty(value = {"token", "bot-username"}, prefix = "sessionbot.telegram")
@EnableConfigurationProperties(CommandsSessionBotProperties.class)
@ComponentScan("com.sessionbot")
public class CommandsSessionBotConfiguration {

    static {
        ApiContextInitializer.init();
    }

    @Bean
    @ConditionalOnMissingBean
    public TelegramBotsApi telegramBotsApi(CommandsSessionBot bot) throws TelegramApiRequestException {
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi();
        telegramBotsApi.registerBot(bot);
        return telegramBotsApi;
    }
}
