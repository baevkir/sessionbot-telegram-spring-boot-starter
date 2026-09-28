package io.github.baevkir.sessionbot.i18n;

import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Locale;

/** Default {@link LocaleProvider}: one configured language for the whole bot, ignoring the user. */
public class ConfiguredLocaleProvider implements LocaleProvider {

    private final Locale locale;

    public ConfiguredLocaleProvider(Locale locale) {
        this.locale = locale;
    }

    @Override
    public Locale getLocale(User user) {
        return locale;
    }
}
