package io.github.baevkir.sessionbot.fixtures;

import io.github.baevkir.sessionbot.CommandContext;
import io.github.baevkir.sessionbot.annotation.BotCommand;
import io.github.baevkir.sessionbot.annotation.CommandMethod;
import io.github.baevkir.sessionbot.annotation.Parameter;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@BotCommand(value = "order", description = "Order fixture", hidden = false)
public class OrderCommand {

    private final List<String> capturedContextAnswers = new ArrayList<>();

    @CommandMethod(arguments = "buy&{product}")
    public Mono<SendMessage> buy(@Parameter("product") String product, CommandContext context) {
        capturedContextAnswers.clear();
        capturedContextAnswers.addAll(context.getAnswers());
        return Mono.just(SendMessage.builder().chatId(Fixtures.CHAT_ID + "").text("buy:" + product).build());
    }

    @CommandMethod(arguments = "qty&{count}")
    public SendMessage qty(@Parameter("count") Long count) {
        return SendMessage.builder().chatId(Fixtures.CHAT_ID + "").text("qty:" + count).build();
    }

    @CommandMethod(arguments = "schedule&{date}")
    public SendMessage schedule(@Parameter("date") LocalDate date) {
        return SendMessage.builder().chatId(Fixtures.CHAT_ID + "").text("date:" + date).build();
    }

    @CommandMethod(arguments = "note&{required}&{optional}")
    public SendMessage note(
        @Parameter("required") String required,
        @Parameter(value = "optional", required = false) String optional) {
        return SendMessage.builder().chatId(Fixtures.CHAT_ID + "").text("note:" + required + "/" + optional).build();
    }

    public List<String> capturedContextAnswers() {
        return capturedContextAnswers;
    }
}