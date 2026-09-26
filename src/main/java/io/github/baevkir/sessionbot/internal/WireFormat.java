package io.github.baevkir.sessionbot.internal;

/**
 * Percent-escaping for values carried in the callback wire format. Only the characters the format
 * itself uses are escaped, so ordinary values stay readable and within Telegram's 64-byte callback
 * limit: {@code % ? & #} everywhere, plus {@code :} in dynamic parameters, where it splits key from
 * value. Any other {@code %} sequence is left untouched on decode.
 */
public final class WireFormat {

    private static final String ANSWER_RESERVED = "%?&#";
    private static final String RESERVED = "%?&#:";

    private WireFormat() {
    }

    public static String encodeAnswer(String value) {
        return encode(value, ANSWER_RESERVED);
    }

    public static String encodeParam(String value) {
        return encode(value, RESERVED);
    }

    public static String decode(String value) {
        if (value == null || value.indexOf('%') < 0) {
            return value;
        }
        var result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            var character = value.charAt(i);
            if (character == '%' && i + 2 < value.length()) {
                var decoded = reservedCharacter(value.substring(i + 1, i + 3));
                if (decoded != null) {
                    result.append(decoded);
                    i += 2;
                    continue;
                }
            }
            result.append(character);
        }
        return result.toString();
    }

    private static String encode(String value, String reserved) {
        if (value == null) {
            return null;
        }
        var result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            var character = value.charAt(i);
            if (reserved.indexOf(character) >= 0) {
                result.append('%').append(String.format("%02X", (int) character));
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static Character reservedCharacter(String hex) {
        try {
            var character = (char) Integer.parseInt(hex, 16);
            return RESERVED.indexOf(character) >= 0 ? character : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
