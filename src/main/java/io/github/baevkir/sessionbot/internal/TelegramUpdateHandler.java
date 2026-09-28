package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.MessageExecutor;
import io.github.baevkir.sessionbot.AuthInterceptor;
import io.github.baevkir.sessionbot.handler.ContactHandler;
import io.github.baevkir.sessionbot.handler.DocumentHandler;
import io.github.baevkir.sessionbot.error.BotAuthException;
import io.github.baevkir.sessionbot.error.BotCommandException;
import io.github.baevkir.sessionbot.guard.GuardContext;
import io.github.baevkir.sessionbot.guard.GuardDeniedHandler;
import io.github.baevkir.sessionbot.CommandContext;
import io.github.baevkir.sessionbot.UpdateWrapper;
import io.github.baevkir.sessionbot.handler.TextHandler;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Per-chat fold/dispatch: folds a chat's {@link UpdateWrapper} stream into an evolving
 * {@link CommandContext} and dispatches the matched command, executing its emitted prompts inline
 * via {@link MessageExecutor} (so the {@code progress}-state {@code addQuestionMessage} coupling
 * sees the executed {@link Message}). Each context's results are collected, and the stream
 * completes once a command reaches {@link ContextState#close} — so the per-chat resources are
 * released promptly instead of lingering.
 */
@Slf4j
public class TelegramUpdateHandler {

    private final CommandsFactory commandsFactory;
    private final AuthInterceptor authInterceptor;
    private final MessageExecutor messageExecutor;
    private final List<DocumentHandler> documentHandlers;
    private final List<ContactHandler> contactHandlers;
    private final List<TextHandler> textHandlers;
    private final List<String> permitCommands;
    private final GuardDeniedHandler guardDeniedHandler;
    private final String botUsername;

    public TelegramUpdateHandler(
        CommandsFactory commandsFactory,
        AuthInterceptor authInterceptor,
        MessageExecutor messageExecutor,
        List<DocumentHandler> documentHandlers,
        List<ContactHandler> contactHandlers,
        List<TextHandler> textHandlers,
        List<String> permitCommands
    ) {
        this(commandsFactory, authInterceptor, messageExecutor, documentHandlers, contactHandlers, textHandlers,
            permitCommands, new HelpGuardDeniedHandler(commandsFactory.getHelpCommand()), null);
    }

    public TelegramUpdateHandler(
        CommandsFactory commandsFactory,
        AuthInterceptor authInterceptor,
        MessageExecutor messageExecutor,
        List<DocumentHandler> documentHandlers,
        List<ContactHandler> contactHandlers,
        List<TextHandler> textHandlers,
        List<String> permitCommands,
        GuardDeniedHandler guardDeniedHandler
    ) {
        this(commandsFactory, authInterceptor, messageExecutor, documentHandlers, contactHandlers, textHandlers,
            permitCommands, guardDeniedHandler, null);
    }

    public TelegramUpdateHandler(
        CommandsFactory commandsFactory,
        AuthInterceptor authInterceptor,
        MessageExecutor messageExecutor,
        List<DocumentHandler> documentHandlers,
        List<ContactHandler> contactHandlers,
        List<TextHandler> textHandlers,
        List<String> permitCommands,
        GuardDeniedHandler guardDeniedHandler,
        String botUsername
    ) {
        this.commandsFactory = commandsFactory;
        this.authInterceptor = authInterceptor;
        this.messageExecutor = messageExecutor;
        this.documentHandlers = documentHandlers;
        this.contactHandlers = contactHandlers;
        this.textHandlers = textHandlers;
        this.permitCommands = permitCommands == null ? List.of() : List.copyOf(permitCommands);
        this.guardDeniedHandler = Objects.requireNonNull(guardDeniedHandler, "guardDeniedHandler");
        this.botUsername = botUsername == null || !botUsername.startsWith("@") ? botUsername : botUsername.substring(1);
    }

    public Flux<PartialBotApiMethod<?>> handleUpdates(Flux<UpdateWrapper> updates) {
        Assert.notNull(updates, "Updates is null.");
        return updates
            .filter(this::addressedToThisBot)
            .scanWith(ConversationState::empty, this::fold)
            .skip(1) // drop the empty seed context emitted before any update
            .concatMap(context ->
                dispatch(context)
                    .collectList()
                    .map(results -> new DispatchOutcome(context, results)))
            .takeUntil(outcome -> ContextState.close.equals(outcome.context().getState()))
            .concatMapIterable(DispatchOutcome::results);
    }

    private ConversationState fold(ConversationState context, UpdateWrapper update) {
        if (update.isCommand()) {
            return ConversationState.forCommand(update);
        }
        if (!context.hasCommand()) {
            // Each update outside a command gets its own context, so they never pile up in one.
            return ConversationState.forBareUpdate(update);
        }
        if (update.getDynamicParams().needRefreshContext()) {
            return ConversationState.forCommand(context.getCommandUpdate()).addUpdate(update);
        }
        return context.addUpdate(update);
    }

    /** In a group, {@code /order@OtherBot} is meant for another bot: leave it alone. */
    private boolean addressedToThisBot(UpdateWrapper update) {
        var addressee = update.getAddressee();
        if (botUsername == null || addressee.isEmpty() || addressee.get().equalsIgnoreCase(botUsername)) {
            return true;
        }
        log.debug("Ignoring /{} addressed to @{} in chat {}", update.getCommand(), addressee.get(), update.getChatId());
        return false;
    }

    private Flux<PartialBotApiMethod<?>> dispatch(ConversationState context) {
        if (!context.hasCommand()) {
            return dispatchOutsideCommand(context)
                .orElseGet(() -> Flux.<PartialBotApiMethod<?>>from(commandsFactory.getHelpCommand().render(context))
                    .doOnNext(messageExecutor::execute));
        }
        log.debug("Dispatching command '{}' in chat {} (state={})", context.getCommand(), context.getChatId(), context.getState());
        Mono<Boolean> authorized = permitCommands.contains(context.getCommand())
                ? Mono.just(true)
                : authInterceptor.intercept(context);
        return authorized
            .<PartialBotApiMethod<?>>flatMapMany(allowed -> {
                if (!allowed) {
                    log.debug("Auth rejected for command '{}' in chat {} (user={})", context.getCommand(), context.getChatId(), userName(context));
                    return Flux.error(new BotAuthException(context, "User " + userName(context) + " is unauthorized to use bot."));
                }
                var command = commandsFactory.getCommand(context.getCommand());
                return CommandGuards.permits(command, GuardContext.of(context, command.getCommandIdentifier()))
                    .<PartialBotApiMethod<?>>flatMapMany(permitted -> {
                        if (!permitted) {
                            log.debug("Guard denied command '{}' in chat {}", context.getCommand(), context.getChatId());
                            context.close();
                            return Flux.<PartialBotApiMethod<?>>from(guardDeniedHandler.onDenied(context));
                        }
                        return Flux.<PartialBotApiMethod<?>>from(command.process(context));
                    });
            })
            .doOnNext(message -> {
                var result = messageExecutor.execute(message);
                if (result instanceof Message resultMessage && ContextState.progress.equals(context.getState())) {
                    context.addQuestionMessage(resultMessage);
                }
            });
    }

    private Optional<Flux<PartialBotApiMethod<?>>> dispatchOutsideCommand(ConversationState context) {
        var update = context.getCommandUpdate();
        var contactDispatch = update.getContact()
            .flatMap(contact -> contactHandlers.stream()
                .filter(handler -> handler.supports(contact))
                .findFirst()
                .map(handler -> dispatchBare(context, "contact " + contact.getUserId(),
                    bareContext -> handler.handle(bareContext, contact))));
        if (contactDispatch.isPresent()) {
            return contactDispatch;
        }
        var documentDispatch = update.getDocument()
            .flatMap(document -> documentHandlers.stream()
                .filter(handler -> handler.supports(document))
                .findFirst()
                .map(handler -> dispatchBare(context, "document '" + document.getFileName() + "'",
                    bareContext -> handler.handle(bareContext, document))));
        if (documentDispatch.isPresent()) {
            return documentDispatch;
        }
        return messageText(update)
            .flatMap(text -> textHandlers.stream()
                .filter(handler -> handler.supports(text))
                .findFirst()
                .map(handler -> dispatchBare(context, "text", bareContext -> handler.handle(bareContext, text))));
    }

    private Flux<PartialBotApiMethod<?>> dispatchBare(ConversationState context, String description,
                                                     Function<CommandContext, Publisher<? extends PartialBotApiMethod<?>>> handling) {
        log.debug("Dispatching {} in chat {}", description, context.getChatId());
        return authInterceptor.intercept(context)
            .<PartialBotApiMethod<?>>flatMapMany(authorized -> {
                if (!authorized) {
                    return Flux.error(new BotAuthException(context, "User " + userName(context) + " is unauthorized to use bot."));
                }
                return Flux.<PartialBotApiMethod<?>>from(handling.apply(context));
            })
            .onErrorMap(error -> error instanceof BotCommandException || error instanceof BotAuthException
                ? error
                : new BotCommandException(context, error))
            .doOnNext(messageExecutor::execute);
    }

    private static String userName(CommandContext context) {
        return context.getUser() != null ? context.getUser().getUserName() : "unknown";
    }

    /** The message text exactly as typed; a callback's data is not "text" and never reaches a handler. */
    private static Optional<String> messageText(UpdateWrapper update) {
        return Optional.ofNullable(update.getUpdate().getMessage())
            .map(Message::getText)
            .filter(StringUtils::hasText);
    }

    /**
     * A dispatched context paired with the results it produced. Carrying the context lets the
     * pipeline complete after the outcome whose command reached {@link ContextState#close}, while
     * still emitting all of that context's results (including cleanup messages) first.
     */
    private record DispatchOutcome(ConversationState context, List<PartialBotApiMethod<?>> results) {
    }
}
