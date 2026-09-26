package com.kb.sessionbot.menu;

import com.kb.sessionbot.MessageExecutor;
import com.kb.sessionbot.commands.CommandsFactory;
import com.kb.sessionbot.commands.IBotCommand;
import com.kb.sessionbot.guard.CommandGuards;
import com.kb.sessionbot.guard.GuardContext;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.commands.DeleteMyCommands;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeChat;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Predicate;

/**
 * Per-chat command menus for bots with {@code @Guarded} commands. The library never calls this itself:
 * the application decides when a chat's menu should change (at startup, after a role change, ...).
 * Telegram clients cache menus, so a change may show with a delay; access never depends on the menu,
 * because guards are checked on every call.
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
            .filter(Predicate.not(IBotCommand::hidden))
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
        return Mono.fromRunnable(() -> messageExecutor.execute(method));
    }

    private static BotCommandScopeChat chatScope(String chatId) {
        return BotCommandScopeChat.builder().chatId(chatId).build();
    }
}
