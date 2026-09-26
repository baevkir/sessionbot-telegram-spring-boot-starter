package io.github.baevkir.sessionbot.i18n;

import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Locale;

/** Resolves the bot's language for a caller. */
public interface LocaleProvider {
    /**
     * @param user the Telegram user the text is for; {@code null} for bot-wide text (the default command
     *             menu, out-of-band messages with no recipient). Implementations must tolerate null.
     */
    Locale getLocale(User user);
}
