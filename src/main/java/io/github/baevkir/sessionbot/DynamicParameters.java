package io.github.baevkir.sessionbot;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import static io.github.baevkir.sessionbot.internal.CommandConstants.*;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DynamicParameters {
    private final Map<String, String> params;
    
    public static DynamicParameters create(Map<String, String> params) {
        return new DynamicParameters(Collections.unmodifiableMap(Objects.requireNonNull(params, "Params is null.")));
    }

    public static DynamicParameters empty() {
        return new DynamicParameters(Collections.emptyMap());
    }

    public String getParam(String param) {
        return params.get(param);
    }

    public boolean hasParam(String param) {
        return params.containsKey(param);
    }

    public boolean isEmpty() {
        return params.isEmpty();
    }

    public boolean needRefreshContext() {
        return params.containsKey(REFRESH_CONTEXT_DYNAMIC_PARAM);
    }

    public boolean canSkipAnswer(int index) {
        if (!params.containsKey(SKIP_ANSWER_DYNAMIC_PARAM)) {
            return false;
        }
        try {
            return Integer.parseInt(params.get(SKIP_ANSWER_DYNAMIC_PARAM)) >= index;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
