package com.kb.sessionbot.auth;

import com.kb.sessionbot.model.CommandContext;
import reactor.core.publisher.Mono;

public interface AuthInterceptor {
    Mono<Boolean> intercept(CommandContext context);
}
