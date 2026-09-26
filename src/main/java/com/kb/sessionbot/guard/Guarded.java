package com.kb.sessionbot.guard;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts a {@code @BotCommand} to callers every listed {@link CommandGuard} permits. A guarded
 * command is hidden from the default command menu and from {@code /help} for callers it denies, and
 * refused on every update when invoked anyway. Works as a meta-annotation, so a rule can be named once:
 * <pre>{@code
 * @Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME)
 * @Guarded(AdminGuard.class)
 * public @interface AdminOnly {}
 * }</pre>
 * Several {@code @Guarded} found on one command (directly and through meta-annotations) combine with AND.
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Guarded {
    Class<? extends CommandGuard>[] value();
}
