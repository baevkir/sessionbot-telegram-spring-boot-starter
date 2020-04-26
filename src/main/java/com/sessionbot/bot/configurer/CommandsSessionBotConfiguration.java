package com.sessionbot.bot.configurer;

import com.sessionbot.bot.CommandsFactory;
import com.sessionbot.bot.CommandsSessionBot;
import com.sessionbot.bot.errors.handler.ErrorHandlerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass({CommandsSessionBot.class, CommandsFactory.class, ErrorHandlerFactory.class})
@EnableConfigurationProperties(CommandsSessionBotProperties.class)
@ComponentScan("com.sessionbot")
public class CommandsSessionBotConfiguration {
}
