package io.github.baevkir.sessionbot.i18n;

import io.github.baevkir.sessionbot.CommandContext;
import org.springframework.context.MessageSource;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.DayOfWeek;
import java.time.Month;

/**
 * Facade for built-in label resolution. Typed accessors resolve library label keys from the
 * configured-locale bundle; {@link #resolve} handles consumer-authored {@code {key}} /
 * {@code {key:default}} annotation text. Locale comes from {@link LocaleProvider} for the caller
 * (the {@link CommandContext}'s user; may be null).
 */
public class BotLabels {

    private final MessageSource messages;
    private final LocaleProvider localeProvider;

    public BotLabels(MessageSource messages, LocaleProvider localeProvider) {
        this.messages = messages;
        this.localeProvider = localeProvider;
    }

    public String helpTitle(CommandContext ctx)        { return get("help.title", ctx); }
    public String helpIntro(CommandContext ctx)        { return get("help.intro", ctx); }
    public String helpDescription(User user)           { return getForUser("help.description", user); }
    public String skip(CommandContext ctx)             { return get("button.skip", ctx); }
    public String yes(CommandContext ctx)              { return get("button.yes", ctx); }
    public String no(CommandContext ctx)               { return get("button.no", ctx); }
    public String back(CommandContext ctx)             { return get("button.back", ctx); }
    public String errorGeneric(CommandContext ctx)     { return get("error.generic", ctx); }
    public String unauthorized(CommandContext ctx)     { return get("error.unauthorized", ctx); }

    public String month(CommandContext ctx, Month month) {
        return get("month." + month.name().toLowerCase().substring(0, 3), ctx);
    }

    public String missingParameter(CommandContext ctx, String field) {
        return get("param.missing", ctx, field);
    }

    public String unsupportedOptions(CommandContext ctx, Object options, Object command) {
        return get("command.unsupportedOptions", ctx, options, command);
    }

    public String dateFormatHint(CommandContext ctx, String label, String format) {
        return get("date.formatHint", ctx, label, format);
    }

    public String weekday(CommandContext ctx, DayOfWeek day) {
        return get("weekday." + day.name().toLowerCase().substring(0, 3), ctx);
    }

    public String resolve(String text, CommandContext ctx) {
        return resolve(text, user(ctx));
    }

    /**
     * Resolve for a user directly — for out-of-band messages and menus that have no incoming
     * {@link CommandContext}. Pass {@code null} for the configured/bot-wide locale.
     */
    public String resolve(String text, User user) {
        if (text == null) {
            return null;
        }
        var trimmed = text.trim();
        if (trimmed.length() > 2 && trimmed.startsWith("{") && trimmed.endsWith("}")
                && trimmed.indexOf('}') == trimmed.length() - 1) {
            var inner = trimmed.substring(1, trimmed.length() - 1);
            int sep = inner.indexOf(':');
            var code = (sep >= 0 ? inner.substring(0, sep) : inner).trim();
            var fallback = sep >= 0 ? inner.substring(sep + 1) : text;
            return messages.getMessage(code, null, fallback, localeProvider.getLocale(user));
        }
        return text;
    }

    private String get(String key, CommandContext ctx, Object... args) {
        return getForUser(key, user(ctx), args);
    }

    private String getForUser(String key, User user, Object... args) {
        return messages.getMessage(key, args, localeProvider.getLocale(user));
    }

    private static User user(CommandContext ctx) {
        return ctx == null ? null : ctx.getUser();
    }
}
