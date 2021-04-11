package com.kb.sessionbot.commands.dispatcher;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import reactor.core.publisher.Mono;

@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
class InvocationResultResolver {
    private Object invocationResult;

    public static InvocationResultResolver of(Object invocationResult) {
        var resolver = new InvocationResultResolver();
        resolver.invocationResult = invocationResult;
        return resolver;
    }

    public Mono<? extends PartialBotApiMethod<?>> resolve() {
        if (invocationResult == null) {
            return Mono.empty();
        }
        if (invocationResult instanceof Mono) {
            return ((Mono<?>) invocationResult).map(this::resolveFlatType);
        }
        return Mono.fromSupplier(() -> resolveFlatType(invocationResult));
    }

    @SuppressWarnings("unchecked")
    private <T extends PartialBotApiMethod<?>> T resolveFlatType(Object value) {
        return (T) value;
    }
}
