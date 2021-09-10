package com.kb.sessionbot.auth;

import com.kb.sessionbot.model.CommandContext;

public interface AuthInterceptor {
    boolean intercept(CommandContext context);
}
