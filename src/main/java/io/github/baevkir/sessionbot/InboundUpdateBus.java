package io.github.baevkir.sessionbot;

import org.telegram.telegrambots.meta.api.objects.Update;
import reactor.core.publisher.Flux;

/**
 * Inbound transport + per-chat partitioning. {@code emit} is called once per received update (by
 * the coordinator's {@code consume}); {@code updates()} emits one {@link ChatUpdateStream} per
 * active chat, each carrying that chat's updates in arrival order.
 *
 * <p>Contract: implementations MUST preserve per-chat arrival order within a stream, and SHOULD
 * complete a chat's stream when it goes idle so downstream fan-out slots are freed. The handler
 * dispatching a stream also completes or cancels it itself, well before any idle timeout, once the
 * chat's conversation closes or errors; an implementation MUST treat that as the end of the current
 * stream for that chat and start a brand new {@link ChatUpdateStream} for it on that chat's next update.
 */
public interface InboundUpdateBus {

    void emit(Update update);

    Flux<ChatUpdateStream> updates();
}
