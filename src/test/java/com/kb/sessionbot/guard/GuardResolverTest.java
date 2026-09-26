package com.kb.sessionbot.guard;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.assertj.core.api.Assertions.assertThat;

class GuardResolverTest {

    static class AllowGuard implements CommandGuard {
        @Override
        public Mono<Boolean> permits(GuardContext context) {
            return Mono.just(true);
        }
    }

    static class OtherGuard implements CommandGuard {
        @Override
        public Mono<Boolean> permits(GuardContext context) {
            return Mono.just(true);
        }
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Guarded(AllowGuard.class)
    @interface AllowOnly {
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Guarded(OtherGuard.class)
    @interface OtherOnly {
    }

    static class Plain {
    }

    @Guarded(AllowGuard.class)
    static class Direct {
    }

    static class DirectChild extends Direct {
    }

    @AllowOnly
    static class Meta {
    }

    @AllowOnly
    @OtherOnly
    static class TwoMeta {
    }

    @AllowOnly
    @Guarded({AllowGuard.class, OtherGuard.class})
    static class DirectAndMetaOverlap {
    }

    @Test
    void aPlainCommandHasNoGuards() {
        assertThat(GuardResolver.guardTypes(Plain.class)).isEmpty();
    }

    @Test
    void aDirectAnnotationIsFound() {
        assertThat(GuardResolver.guardTypes(Direct.class)).containsExactly(AllowGuard.class);
    }

    @Test
    void aSubclassInheritsItsParentsGuards() {
        // Spring hands us CGLIB subclasses of @Configuration-style beans; the guard must survive that.
        assertThat(GuardResolver.guardTypes(DirectChild.class)).containsExactly(AllowGuard.class);
    }

    @Test
    void aMetaAnnotationIsFound() {
        assertThat(GuardResolver.guardTypes(Meta.class)).containsExactly(AllowGuard.class);
    }

    @Test
    void twoMetaAnnotationsCombine() {
        assertThat(GuardResolver.guardTypes(TwoMeta.class))
            .containsExactlyInAnyOrder(AllowGuard.class, OtherGuard.class);
    }

    @Test
    void aGuardNamedTwiceIsListedOnce() {
        assertThat(GuardResolver.guardTypes(DirectAndMetaOverlap.class))
            .containsExactlyInAnyOrder(AllowGuard.class, OtherGuard.class);
    }
}
