package com.kb.sessionbot.model;

import lombok.Builder;
import lombok.Data;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;

@Data
@Builder
public class BotCommandResult {
    private PartialBotApiMethod<?> message;
}

