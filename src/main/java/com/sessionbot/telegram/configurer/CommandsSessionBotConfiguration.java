package com.sessionbot.telegram.configurer;

import com.sessionbot.telegram.CommandsFactory;
import com.sessionbot.telegram.CommandsSessionBot;
import com.sessionbot.telegram.errors.handler.ErrorHandlerFactory;
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
