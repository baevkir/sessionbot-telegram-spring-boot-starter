package com.kb.sessionbot.commands;

/**
 * Escaping for text interpolated into a Telegram {@code HTML} parse-mode message. Telegram accepts
 * only a handful of named entities, so this escapes exactly {@code & < >} rather than using a general
 * HTML escaper, which would emit entities such as {@code &eacute;} that Telegram rejects.
 */
public final class TelegramHtml {

    private TelegramHtml() {
    }

    public static String escape(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
