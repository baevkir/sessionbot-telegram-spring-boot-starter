package com.kb.sessionbot.commands.dispatcher;

import com.kb.sessionbot.model.BotCommandResult;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class InvocationResultResolver {
    private Object invocationResult;

    public static InvocationResultResolver of(Object invocationResult) {
        var resolver = new InvocationResultResolver();
        resolver.invocationResult = invocationResult;
        return resolver;
    }

    public Publisher<BotCommandResult> resolve() {
        if (invocationResult == null) {
            return Mono.empty();
        }
        if (invocationResult instanceof Publisher) {
            return Flux.from((Publisher<?>) invocationResult).map(this::resolveFlatType);
        }
        if (invocationResult instanceof Collection) {
            return Flux.fromIterable((Collection<?>)invocationResult).map(this::resolveFlatType);
        }
        return Mono.fromSupplier(() -> resolveFlatType(invocationResult));
    }

    @SuppressWarnings("unchecked")
    private <T extends PartialBotApiMethod<?>> BotCommandResult resolveFlatType(Object value) {
        if (value instanceof BotCommandResult) {
            return (BotCommandResult) value;
        }
        return BotCommandResult.builder().message((T) value).build();
    }
}
