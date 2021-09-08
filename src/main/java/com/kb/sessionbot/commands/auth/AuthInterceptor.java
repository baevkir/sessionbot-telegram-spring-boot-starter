package com.kb.sessionbot.commands.auth;

import com.kb.sessionbot.commands.model.CommandRequest;

public interface AuthInterceptor {
    boolean intercept(CommandRequest request);
}
