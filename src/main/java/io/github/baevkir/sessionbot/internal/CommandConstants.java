package io.github.baevkir.sessionbot.internal;

public interface CommandConstants {
    String COMMAND_START = "/";
    String ADDRESSEE_SEPARATOR = "@";
    String COMMAND_PARAMETERS_SEPARATOR = "?";
    String PARAMETER_SEPARATOR = "&";
    String KEY_VALUE_SEPARATOR = ":";
    String DYNAMIC_PARAMETERS_SEPARATOR = "#";

    int MAX_CALLBACK_BYTES = 64;
    String NULL_ANSWER = "null";

    String REFRESH_CONTEXT_DYNAMIC_PARAM = "refreshContext";
    String SKIP_ANSWER_DYNAMIC_PARAM = "skip";
}
