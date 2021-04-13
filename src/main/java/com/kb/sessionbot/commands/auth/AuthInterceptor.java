package com.kb.sessionbot.commands.auth;

import org.telegram.telegrambots.meta.api.objects.Update;

public interface AuthInterceptor {
    boolean intercept(Update update);
}
