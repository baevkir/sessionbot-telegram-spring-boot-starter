package com.sessionbot.telegram;

import com.sessionbot.telegram.configurer.CommandsSessionBotConfiguration;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = CommandsSessionBotConfiguration.class)
public class SpringContextTest {

    @Autowired
    private CommandsSessionBot commandsSessionBot;

    @Test
    public void whenSpringContextIsBootstrapped_thenNoExceptions() {
        Assert.assertNotNull(commandsSessionBot);
    }
}
