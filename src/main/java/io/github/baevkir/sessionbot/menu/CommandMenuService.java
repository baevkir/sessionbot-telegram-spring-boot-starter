package io.github.baevkir.sessionbot.menu;

import io.github.baevkir.sessionbot.internal.CommandMenus;
import io.github.baevkir.sessionbot.MessageExecutor;
import io.github.baevkir.sessionbot.internal.CommandsFactory;
import io.github.baevkir.sessionbot.internal.RegisteredCommand;
import io.github.baevkir.sessionbot.internal.CommandGuards;
import io.github.baevkir.sessionbot.guard.GuardContext;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.commands.DeleteMyCommands;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeChat;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.function.Predicate;

/**
 * Per-chat command menus for bots with {@code @Guarded} commands. The library never calls this itself:
 * the application decides when a chat's menu should change (at startup, after a role change, ...).
 * Telegram clients cache menus, so a change may show with a delay; access never depends on the menu,
 * because guards are checked on every call. {@link #refresh} evaluates guards with {@code chatType}
 * {@code "private"}, since per-chat menus are meant for private chats; group-chat scopes are out of
 * scope for now.
 *
 * <p>{@link #execute} runs the blocking Telegram call on {@link Schedulers#boundedElastic()}, so
 * calling {@link #refresh} or {@link #reset} from a Netty/WebFlux event-loop thread never blocks it.
 * A Telegram failure is reported through the configured {@link io.github.baevkir.sessionbot.MessageExecutor}
 * (the default, {@link io.github.baevkir.sessionbot.internal.TelegramClientMessageExecutor}, logs it and returns
 * {@code null}), so the {@link Mono} returned here still completes normally even when Telegram
 * rejected the call.
 */
public class CommandMenuService {

    private static final String PRIVATE_CHAT = "private";

    private final CommandsFactory commandsFactory;
    private final MessageExecutor messageExecutor;

    public CommandMenuService(CommandsFactory commandsFactory, MessageExecutor messageExecutor) {
        this.commandsFactory = commandsFactory;
        this.messageExecutor = messageExecutor;
    }

    /**
     * Gives the chat a menu of every command {@code user} may use when at least one of them is guarded;
     * otherwise {@link #reset}s it. Safe to call for anyone.
     */
    public Mono<Void> refresh(String chatId, User user) {
        return Flux.fromIterable(commandsFactory.getCommands())
            .filter(Predicate.not(RegisteredCommand::hidden))
            .concatMap(command -> CommandGuards.permits(command, new GuardContext(user, chatId, PRIVATE_CHAT, command.getCommandIdentifier()))
                .filter(Boolean::booleanValue)
                .map(permitted -> command))
            .collectList()
            .flatMap(permitted -> permitted.stream().anyMatch(command -> !command.guards().isEmpty())
                ? execute(SetMyCommands.builder()
                    .scope(chatScope(chatId))
                    .commands(CommandMenus.toBotCommands(permitted, user == null ? null : user.getUserName()))
                    .build())
                : reset(chatId));
    }

    /** Drops the chat's own menu, so it falls back to the default one. */
    public Mono<Void> reset(String chatId) {
        return execute(DeleteMyCommands.builder().scope(chatScope(chatId)).build());
    }

    private Mono<Void> execute(BotApiMethod<Boolean> method) {
        return Mono.<Void>fromRunnable(() -> messageExecutor.execute(method))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private static BotCommandScopeChat chatScope(String chatId) {
        return BotCommandScopeChat.builder().chatId(chatId).build();
    }
}
