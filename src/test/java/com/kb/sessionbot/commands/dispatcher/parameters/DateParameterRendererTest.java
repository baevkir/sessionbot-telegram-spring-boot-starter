package com.kb.sessionbot.commands.dispatcher.parameters;

import com.kb.sessionbot.fixtures.Fixtures;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class DateParameterRendererTest {

    @Test
    void theMonthHeaderUsesTheBotLanguage() {
        var renderer = new DateParameterRenderer(Fixtures.labels(Locale.forLanguageTag("uk")));
        var context = Fixtures.contextFor("/order").addUpdate(Fixtures.answerWrapper(2, 555, "#date-renderer-date:2026-09-01"));
        var request = ParameterRequest.builder()
            .context(context).index(0).text("Дата").parameterType(LocalDate.class).required(true).build();

        StepVerifier.create(Mono.from(renderer.render(request)))
            .assertNext(method -> {
                var rows = ((InlineKeyboardMarkup) ((SendMessage) method).getReplyMarkup()).getKeyboard();
                assertThat(rows.getFirst().getFirst().getText()).isEqualTo("Вересень 2026");
            })
            .verifyComplete();
    }
}
