package io.github.baevkir.sessionbot.render;

import io.github.baevkir.sessionbot.fixtures.Fixtures;
import io.github.baevkir.sessionbot.CommandContext;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class TimeParameterRendererTest {

    private final TimeParameterRenderer renderer = new TimeParameterRenderer(Fixtures.labels(Locale.forLanguageTag("uk")));

    private static CommandContext commandContext() {
        return Fixtures.contextFor("/order");
    }

    private static CommandContext tapped(String callbackData) {
        return commandContext().addUpdate(Fixtures.answerWrapper(2, 555, callbackData));
    }

    private static ParameterRequest request(CommandContext context, boolean required) {
        return ParameterRequest.builder()
            .context(context)
            .index(0)
            .text("Час")
            .parameterType(LocalTime.class)
            .required(required)
            .build();
    }

    @Test
    void firstRenderSendsTheHourGrid() {
        StepVerifier.create(Mono.from(renderer.render(request(commandContext(), true))))
            .assertNext(method -> {
                assertThat(method).isInstanceOf(SendMessage.class);
                var rows = ((InlineKeyboardMarkup) ((SendMessage) method).getReplyMarkup()).getKeyboard();
                assertThat(rows).hasSize(4);
                assertThat(rows).allSatisfy(row -> assertThat(row).hasSize(6));
                assertThat(rows.getFirst().getFirst().getText()).isEqualTo("00");
                assertThat(rows.getLast().getLast().getText()).isEqualTo("23");
                assertThat(rows.get(3).get(1).getText()).isEqualTo("19");
                assertThat(rows.get(3).get(1).getCallbackData())
                    .contains("time-renderer-hour:19")
                    .contains("time-renderer-continue");
            })
            .verifyComplete();
    }

    @Test
    void tappingAnHourEditsTheSameMessageIntoItsQuarterHours() {
        var context = tapped("#time-renderer-hour:19&time-renderer-continue");

        StepVerifier.create(Mono.from(renderer.render(request(context, true))))
            .assertNext(method -> {
                assertThat(method).isInstanceOf(EditMessageReplyMarkup.class);
                var edit = (EditMessageReplyMarkup) method;
                assertThat(edit.getMessageId()).isEqualTo(555);
                var rows = edit.getReplyMarkup().getKeyboard();
                assertThat(rows.getFirst()).extracting(InlineKeyboardButton::getText)
                    .containsExactly("19:00", "19:15", "19:30", "19:45");
                assertThat(rows.getFirst().get(2).getCallbackData()).isEqualTo("19:30");
                assertThat(rows.get(1).getFirst().getText()).isEqualTo("Назад");
                assertThat(rows).hasSize(2);
            })
            .verifyComplete();
    }

    @Test
    void backReturnsToTheHourGridInPlace() {
        var context = tapped("#time-renderer-continue");

        StepVerifier.create(Mono.from(renderer.render(request(context, true))))
            .assertNext(method -> {
                assertThat(method).isInstanceOf(EditMessageReplyMarkup.class);
                assertThat(((EditMessageReplyMarkup) method).getReplyMarkup().getKeyboard()).hasSize(4);
            })
            .verifyComplete();
    }

    @Test
    void anOptionalTimeGetsASkipRow() {
        StepVerifier.create(Mono.from(renderer.render(request(commandContext(), false))))
            .assertNext(method -> {
                var rows = ((InlineKeyboardMarkup) ((SendMessage) method).getReplyMarkup()).getKeyboard();
                assertThat(rows).hasSize(5);
                assertThat(rows.getLast()).extracting(InlineKeyboardButton::getText).containsExactly("Пропустити");
            })
            .verifyComplete();
    }

    @Test
    void theFactoryRoutesLocalTimeToTheTimeRenderer() {
        ParameterRenderer wrongRenderer = request -> Mono.error(new AssertionError("wrong renderer"));
        var factory = new CompositeParameterRenderer(wrongRenderer, wrongRenderer, wrongRenderer, renderer);

        StepVerifier.create(Mono.from(factory.render(request(commandContext(), true))))
            .assertNext(method -> assertThat(method).isInstanceOf(SendMessage.class))
            .verifyComplete();
    }
}
