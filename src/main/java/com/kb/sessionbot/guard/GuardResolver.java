package com.kb.sessionbot.guard;

import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.util.ClassUtils;

import java.util.Arrays;
import java.util.List;

/** Collects every {@link Guarded} on a command class - direct, inherited or meta-present. */
public final class GuardResolver {

    private GuardResolver() {
    }

    public static List<Class<? extends CommandGuard>> guardTypes(Class<?> commandType) {
        return MergedAnnotations.from(ClassUtils.getUserClass(commandType), SearchStrategy.TYPE_HIERARCHY)
            .stream(Guarded.class)
            .flatMap(annotation -> Arrays.stream(annotation.getClassArray("value")))
            .<Class<? extends CommandGuard>>map(type -> type.asSubclass(CommandGuard.class))
            .distinct()
            .toList();
    }
}
