package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.error.BotAuthErrorHandler;
import io.github.baevkir.sessionbot.error.BotCommandErrorHandler;
import io.github.baevkir.sessionbot.error.ErrorHandler;
import io.github.baevkir.sessionbot.error.BotAuthException;
import io.github.baevkir.sessionbot.error.BotCommandException;
import io.github.baevkir.sessionbot.fixtures.Fixtures;
import io.github.baevkir.sessionbot.CommandContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorHandlerFactoryTest {

    private CommandContext context;
    private ErrorHandlerFactory factory;

    @BeforeEach
    void setUp() {
        context = Fixtures.contextFor("/order");
        factory = new ErrorHandlerFactory(
            List.<ErrorHandler<?>>of(new BotCommandErrorHandler(Fixtures.labels(Locale.ENGLISH)), new BotAuthErrorHandler(Fixtures.labels(Locale.ENGLISH))));
        factory.init();
    }

    @Test
    @DisplayName("routes BotAuthException to its handler, replying with the localized refusal")
    void routesAuthException() {
        StepVerifier.create(factory.handle(new BotAuthException(context, "User bob is unauthorized to use bot.")))
            .assertNext(m -> {
                assertThat(m).isInstanceOf(SendMessage.class);
                assertThat(((SendMessage) m).getText()).isEqualTo("You are not allowed to use this bot.");
                assertThat(((SendMessage) m).getChatId()).isEqualTo(String.valueOf(Fixtures.CHAT_ID));
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("walks the cause chain root-outward and routes BotCommandException to its handler")
    void routesCommandExceptionThroughTheCauseChain() {
        // getThrowableList = [BotCommandException, IllegalStateException]; reversed walk checks the
        // root (IllegalStateException, no handler) first, then matches the outer BotCommandException.
        var exception = new BotCommandException(context, new IllegalStateException("boom"));
        StepVerifier.create(factory.handle(exception))
            .assertNext(m -> assertThat(((SendMessage) m).getText()).isEqualTo("Something went wrong. Please try again later."))
            .verifyComplete();
    }

    @Test
    @DisplayName("the root cause's message never reaches the user")
    void rootCauseMessageIsNotShown() {
        var exception = new BotCommandException(context, new IllegalStateException("SELECT * FROM users failed"));
        StepVerifier.create(factory.handle(exception))
            .assertNext(m -> assertThat(((SendMessage) m).getText()).doesNotContain("SELECT"))
            .verifyComplete();
    }

    @Test
    @DisplayName("a handler registered for a base exception handles its subclasses")
    void baseTypeHandlerCatchesSubclass() {
        var factory = new ErrorHandlerFactory(List.<ErrorHandler<?>>of(
            new BotCommandErrorHandler(Fixtures.labels(Locale.ENGLISH)), new DomainErrorHandler()));
        factory.init();
        var exception = new BotCommandException(context, new OrderNotFoundException());
        StepVerifier.create(factory.handle(exception))
            .assertNext(m -> assertThat(((SendMessage) m).getText()).isEqualTo("domain"))
            .verifyComplete();
    }

    @Test
    @DisplayName("the handler for the exact type wins over one for its superclass")
    void exactTypeWinsOverSuperclass() {
        var factory = new ErrorHandlerFactory(List.<ErrorHandler<?>>of(new DomainErrorHandler(), new OrderNotFoundHandler()));
        factory.init();
        StepVerifier.create(factory.handle(new BotCommandException(context, new OrderNotFoundException())))
            .assertNext(m -> assertThat(((SendMessage) m).getText()).isEqualTo("order-not-found"))
            .verifyComplete();
    }

    static class DomainException extends RuntimeException { }

    static class OrderNotFoundException extends DomainException { }

    class DomainErrorHandler implements ErrorHandler<DomainException> {
        @Override
        public Mono<? extends PartialBotApiMethod<?>> handle(DomainException exception) {
            return Mono.just(SendMessage.builder().chatId(context.getChatId()).text("domain").build());
        }
    }

    class OrderNotFoundHandler implements ErrorHandler<OrderNotFoundException> {
        @Override
        public Mono<? extends PartialBotApiMethod<?>> handle(OrderNotFoundException exception) {
            return Mono.just(SendMessage.builder().chatId(context.getChatId()).text("order-not-found").build());
        }
    }

    @Test
    @DisplayName("unhandled exception type yields empty (swallowed)")
    void noHandlerYieldsEmpty() {
        StepVerifier.create(factory.handle(new IllegalStateException("unmapped")))
            .verifyComplete();
    }

    abstract static class BaseCommandHandler implements ErrorHandler<BotCommandException> { }

    static class SubclassedCommandHandler extends BaseCommandHandler {
        @Override
        public reactor.core.publisher.Mono<? extends org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod<?>> handle(BotCommandException exception) {
            return reactor.core.publisher.Mono.fromSupplier(() ->
                SendMessage.builder().chatId(exception.getContext().getChatId()).text("handled-by-subclass").build());
        }
    }

    @Test
    @DisplayName("resolves the exception type from a handler that declares ErrorHandler on a superclass")
    void resolvesTypeArgumentThroughSuperclass() {
        var factory = new ErrorHandlerFactory(List.<ErrorHandler<?>>of(new SubclassedCommandHandler()));
        factory.init();
        var ex = new BotCommandException(context, new IllegalStateException("boom"));
        StepVerifier.create(factory.handle(ex))
            .assertNext(m -> assertThat(((SendMessage) m).getText()).isEqualTo("handled-by-subclass"))
            .verifyComplete();
    }
}