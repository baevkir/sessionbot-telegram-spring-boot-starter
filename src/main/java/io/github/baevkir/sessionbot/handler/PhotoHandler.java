package io.github.baevkir.sessionbot.handler;

import io.github.baevkir.sessionbot.CommandContext;
import org.reactivestreams.Publisher;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;

import java.util.List;

/**
 * Handles a photo sent outside any command flow. Implemented as Spring beans by the host bot;
 * the first handler whose {@link #supports} matches wins. The list holds the sizes Telegram sent
 * for that one photo; the caption and media group id are read from the context's command update
 * ({@code getCaption()}, {@code getMediaGroupId()}). When no handler matches (or none are
 * registered) the update falls through to the default help behavior.
 */
public interface PhotoHandler {

    boolean supports(List<PhotoSize> photos);

    Publisher<? extends PartialBotApiMethod<?>> handle(CommandContext context, List<PhotoSize> photos);
}
