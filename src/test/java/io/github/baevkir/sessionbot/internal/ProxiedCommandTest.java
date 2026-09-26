package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.annotation.BotCommand;
import io.github.baevkir.sessionbot.annotation.CommandMethod;
import io.github.baevkir.sessionbot.fixtures.FixtureCommandConfig;
import io.github.baevkir.sessionbot.fixtures.Fixtures;
import io.github.baevkir.sessionbot.fixtures.OrderCommand;
import io.github.baevkir.sessionbot.guard.CommandGuard;
import io.github.baevkir.sessionbot.guard.GuardContext;
import io.github.baevkir.sessionbot.guard.Guarded;
import io.github.baevkir.sessionbot.CommandContext;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("commands wrapped in AOP proxies (@Transactional, @PreAuthorize, ...)")
class ProxiedCommandTest {

    private AnnotationConfigApplicationContext context;
    private final AtomicInteger adviceCalls = new AtomicInteger();

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.register(FixtureCommandConfig.class);
        context.registerBean(AllowGuard.class, AllowGuard::new);
        context.refresh();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    @DisplayName("a CGLIB proxy dispatches to the annotated method and runs its advice")
    void cglibProxy() {
        var factory = new ProxyFactory(new OrderCommand());
        factory.setProxyTargetClass(true);
        factory.addAdvice(countingAdvice());
        var proxy = factory.getProxy();
        assertThat(AopUtils.isCglibProxy(proxy)).isTrue();

        var dispatcher = new CommandsDispatcher(proxy, context);
        var result = dispatcher.invoke(CommandContext.create(Fixtures.commandWrapper("/order?buy&book")));

        assertThat(dispatcher.getCommandId()).isEqualTo("order");
        assertThat(result.hasErrors()).isFalse();
        StepVerifier.create(result.getInvocation())
            .assertNext(message -> assertThat(((SendMessage) message).getText()).isEqualTo("buy:book"))
            .verifyComplete();
        assertThat(adviceCalls).hasValue(1);
    }

    @Test
    @DisplayName("a JDK interface proxy dispatches through the interface method and runs its advice")
    void jdkProxy() {
        var factory = new ProxyFactory(new GreetCommand());
        factory.addInterface(Greeting.class);
        factory.addAdvice(countingAdvice());
        var proxy = factory.getProxy();
        assertThat(AopUtils.isJdkDynamicProxy(proxy)).isTrue();

        var dispatcher = new CommandsDispatcher(proxy, context);
        var result = dispatcher.invoke(CommandContext.create(Fixtures.commandWrapper("/greet")));

        assertThat(dispatcher.getCommandId()).isEqualTo("greet");
        assertThat(result.hasErrors()).isFalse();
        StepVerifier.create(result.getInvocation())
            .assertNext(message -> assertThat(((SendMessage) message).getText()).isEqualTo("hello"))
            .verifyComplete();
        assertThat(adviceCalls).hasValue(1);
    }

    @Test
    @DisplayName("guards are resolved from the proxied class")
    void guardsThroughProxy() {
        var factory = new ProxyFactory(new GreetCommand());
        factory.addInterface(Greeting.class);

        var command = new DispatcherBotCommand(factory.getProxy(), context);

        assertThat(command.guards()).singleElement().isInstanceOf(AllowGuard.class);
    }

    private MethodInterceptor countingAdvice() {
        return invocation -> {
            adviceCalls.incrementAndGet();
            return invocation.proceed();
        };
    }

    public interface Greeting {
        SendMessage greet();
    }

    @BotCommand("greet")
    @Guarded(AllowGuard.class)
    public static class GreetCommand implements Greeting {

        @Override
        @CommandMethod
        public SendMessage greet() {
            return SendMessage.builder().chatId(Fixtures.CHAT_ID + "").text("hello").build();
        }
    }

    public static class AllowGuard implements CommandGuard {

        @Override
        public Mono<Boolean> permits(GuardContext context) {
            return Mono.just(true);
        }
    }
}
