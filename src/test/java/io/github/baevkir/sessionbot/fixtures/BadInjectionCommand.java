package io.github.baevkir.sessionbot.fixtures;

import io.github.baevkir.sessionbot.annotation.BotCommand;
import io.github.baevkir.sessionbot.annotation.CommandMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.LocalDate;

@BotCommand(value = "badinject", description = "Unsupported auto-injection fixture")
public class BadInjectionCommand {

    // 'when' is neither @Parameter-annotated nor a supported auto-injection type (Update/UpdateWrapper/User/String chatId/DynamicParameters/CommandContext).
    @CommandMethod(arguments = "go")
    public SendMessage go(LocalDate when) {
        return SendMessage.builder().chatId(Fixtures.CHAT_ID + "").text("when:" + when).build();
    }
}