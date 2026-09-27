package io.github.baevkir.sessionbot.guard;

import io.github.baevkir.sessionbot.CommandContext;
import io.github.baevkir.sessionbot.UpdateWrapper;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Optional;

/**
 * What a {@link CommandGuard} knows about the call it is judging. {@code user} is the sender of the
 * command update - the same object the {@code AuthInterceptor} saw and may have normalized - and is
 * {@code null} only when the update carries no sender. In a group chat a conversation belongs to the
 * chat, not to any one member: {@code user} is always the member who opened it, even when a later
 * reply or button tap in that same conversation comes from someone else in the group - so a guarded
 * multi-step command is not safe to expose in a group yet. {@code chatType} is Telegram's
 * {@code private}/{@code group}/{@code supergroup}/{@code channel}, or {@code null} when unknown.
 */
public record GuardContext(User user, String chatId, String chatType, String command) {

    public static GuardContext of(CommandContext context, String command) {
        Optional<UpdateWrapper> update = Optional.ofNullable(context.getCommandUpdate()).or(context::getCurrentUpdate);
        return new GuardContext(
            update.map(UpdateWrapper::getFrom).orElse(null),
            context.getChatId(),
            update.map(UpdateWrapper::getChatType).orElse(null),
            command);
    }
}
