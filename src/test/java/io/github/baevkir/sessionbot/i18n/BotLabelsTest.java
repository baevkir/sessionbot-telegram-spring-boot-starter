package io.github.baevkir.sessionbot.i18n;

import io.github.baevkir.sessionbot.CommandContext;
import io.github.baevkir.sessionbot.fixtures.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.DayOfWeek;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class BotLabelsTest {

    private BotLabels labels(Locale locale) {
        var ms = new ResourceBundleMessageSource();
        ms.setBasenames("sessionbot-labels");
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false);
        var parent = new ResourceBundleMessageSource();
        parent.setBasenames("test-consumer-labels");
        parent.setDefaultEncoding("UTF-8");
        parent.setFallbackToSystemLocale(false);
        ms.setParentMessageSource(parent);
        return new BotLabels(ms, userName -> locale);
    }

    @DisplayName("typed accessors resolve from the configured locale bundle")
    @Test
    void typedAccessorsResolve() {
        assertThat(labels(Locale.ENGLISH).helpTitle(null)).isEqualTo("Help");
        assertThat(labels(Locale.forLanguageTag("uk")).helpTitle(null)).isEqualTo("Довідка");
        assertThat(labels(Locale.ENGLISH).weekday(null, DayOfWeek.SUNDAY)).isEqualTo("Sun");
        assertThat(labels(Locale.forLanguageTag("uk")).weekday(null, DayOfWeek.SUNDAY)).isEqualTo("Нд");
    }

    @DisplayName("missingParameter formats the field argument")
    @Test
    void missingParameterFormatsArg() {
        assertThat(labels(Locale.ENGLISH).missingParameter(null, "product"))
            .isEqualTo("Please provide the field 'product'.");
    }

    @DisplayName("resolve: {key} resolves from the parent message source")
    @Test
    void resolveKnownKey() {
        assertThat(labels(Locale.ENGLISH).resolve("{consumer.greeting}", (User) null)).isEqualTo("Hello");
    }

    @DisplayName("resolve: unknown {key} falls back to the literal; {key:default} uses the default")
    @Test
    void resolveFallbacks() {
        assertThat(labels(Locale.ENGLISH).resolve("{missing.key}", (User) null)).isEqualTo("{missing.key}");
        assertThat(labels(Locale.ENGLISH).resolve("{missing.key:Fallback}", (User) null)).isEqualTo("Fallback");
    }

    @DisplayName("resolve: literal and partial-brace text pass through unchanged")
    @Test
    void resolveLiteral() {
        assertThat(labels(Locale.ENGLISH).resolve("Health check", (User) null)).isEqualTo("Health check");
        assertThat(labels(Locale.ENGLISH).resolve("Order {0} items", (User) null)).isEqualTo("Order {0} items");
    }

    @DisplayName("resolve(text, user) and resolve(text, context) hand the User to the LocaleProvider")
    @Test
    void resolveByUser() {
        var ms = new ResourceBundleMessageSource();
        ms.setBasenames("sessionbot-labels");
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false);
        var labels = new BotLabels(ms, user ->
            user != null && "bob".equals(user.getUserName()) ? Locale.forLanguageTag("uk") : Locale.ENGLISH);

        assertThat(labels.resolve("{help.title}", Fixtures.user("bob"))).isEqualTo("Довідка");
        assertThat(labels.resolve("{help.title}", Fixtures.user("alice"))).isEqualTo("Help");
        assertThat(labels.resolve("{help.title}", (User) null)).isEqualTo("Help");
        var bobsContext = CommandContext.of(Fixtures.messageUpdate(1, Fixtures.CHAT_ID, 100, "/order"));
        assertThat(labels.resolve("{help.title}", bobsContext)).isEqualTo("Help"); // Fixtures' sender is "tester"
    }
}
