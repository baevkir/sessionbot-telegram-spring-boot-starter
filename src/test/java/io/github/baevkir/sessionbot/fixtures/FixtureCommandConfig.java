package io.github.baevkir.sessionbot.fixtures;

import io.github.baevkir.sessionbot.render.BooleanParameterRenderer;
import io.github.baevkir.sessionbot.render.DateParameterRenderer;
import io.github.baevkir.sessionbot.render.ParameterRenderer;
import io.github.baevkir.sessionbot.render.CompositeParameterRenderer;
import io.github.baevkir.sessionbot.render.TextParameterRenderer;
import io.github.baevkir.sessionbot.render.TimeParameterRenderer;
import io.github.baevkir.sessionbot.i18n.BotLabels;
import io.github.baevkir.sessionbot.i18n.ConfiguredLocaleProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

@Configuration
public class FixtureCommandConfig {

    @Bean
    public OrderCommand orderCommand() {
        return new OrderCommand();
    }

    @Bean
    public EchoCommand echoCommand() {
        return new EchoCommand();
    }

    @Bean
    public BotLabels botLabels() {
        var ms = new ResourceBundleMessageSource();
        ms.setBasenames("sessionbot-labels");
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false);
        return new BotLabels(ms, new ConfiguredLocaleProvider(Locale.ENGLISH));
    }

    @Bean
    public ParameterRenderer textParameterRenderer(BotLabels botLabels) {
        return new TextParameterRenderer(botLabels);
    }

    @Bean
    public ParameterRenderer dateParameterRenderer(BotLabels botLabels) {
        return new DateParameterRenderer(botLabels);
    }

    @Bean
    public ParameterRenderer booleanParameterRenderer(BotLabels botLabels) {
        return new BooleanParameterRenderer(botLabels);
    }

    @Bean
    public ParameterRenderer timeParameterRenderer(BotLabels botLabels) {
        return new TimeParameterRenderer(botLabels);
    }

    @Bean
    public ParameterRenderer defaultParameterRenderer(
        ParameterRenderer textParameterRenderer,
        ParameterRenderer dateParameterRenderer,
        ParameterRenderer booleanParameterRenderer,
        ParameterRenderer timeParameterRenderer) {
        return new CompositeParameterRenderer(textParameterRenderer, dateParameterRenderer, booleanParameterRenderer, timeParameterRenderer);
    }
}