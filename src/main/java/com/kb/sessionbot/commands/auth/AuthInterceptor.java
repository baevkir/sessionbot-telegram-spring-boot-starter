package com.kb.sessionbot.commands.auth;

import com.kb.sessionbot.commands.CommandRequest;
import org.telegram.telegrambots.meta.api.objects.Update;

public interface AuthInterceptor {
    boolean intercept(CommandRequest request);
}
