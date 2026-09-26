package io.github.baevkir.sessionbot;

import reactor.core.publisher.Flux;

/** A single chat's ordered stream of updates, tagged with its chat id. */
public record ChatUpdateStream(String chatId, Flux<UpdateWrapper> updates) {}
