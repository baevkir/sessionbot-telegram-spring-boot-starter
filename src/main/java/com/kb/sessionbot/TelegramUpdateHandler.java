package com.kb.sessionbot;

import com.kb.sessionbot.auth.AuthInterceptor;
import com.kb.sessionbot.commands.CommandsFactory;
import com.kb.sessionbot.contacts.ContactHandler;
import com.kb.sessionbot.documents.DocumentHandler;
import com.kb.sessionbot.errors.exception.BotAuthException;
import com.kb.sessionbot.errors.exception.BotCommandException;
import com.kb.sessionbot.guard.CommandGuards;
import com.kb.sessionbot.guard.GuardContext;
import com.kb.sessionbot.guard.GuardDeniedHandler;
import com.kb.sessionbot.model.CommandContext;
import com.kb.sessionbot.model.ContextState;
import com.kb.sessionbot.model.UpdateWrapper;
import com.kb.sessionbot.text.TextHandler;
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
            permitCommands, context -> commandsFactory.getHelpCommand().process(context));
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
        this.commandsFactory = commandsFactory;
        this.authInterceptor = authInterceptor;
        this.messageExecutor = messageExecutor;
        this.documentHandlers = documentHandlers;
        this.contactHandlers = contactHandlers;
        this.textHandlers = textHandlers;
        this.permitCommands = permitCommands == null ? List.of() : List.copyOf(permitCommands);
        this.guardDeniedHandler = Objects.requireNonNull(guardDeniedHandler, "guardDeniedHandler");
    }

    public Flux<PartialBotApiMethod<?>> handleUpdates(Flux<UpdateWrapper> updates) {
        Assert.notNull(updates, "Updates is null.");
        return updates
            .scanWith(CommandContext::empty, this::fold)
            .skip(1) // drop the empty seed context emitted before any update
            .concatMap(context ->
                dispatch(context)
                    .collectList()
                    .map(results -> new DispatchOutcome(context, results)))
            .takeUntil(outcome -> ContextState.close.equals(outcome.context().getState()))
            .concatMapIterable(DispatchOutcome::results);
    }

    private CommandContext fold(CommandContext context, UpdateWrapper update) {
        if (update.isCommand()) {
            return CommandContext.create(update);
        }
        if (update.getDynamicParams().needRefreshContext() && !context.isEmpty()) {
            return CommandContext.create(context.getCommandUpdate()).addUpdate(update);
        }
        return context.addUpdate(update);
    }

    private Flux<PartialBotApiMethod<?>> dispatch(CommandContext context) {
        if (context.isEmpty()) {
            return context.getCurrentUpdate()
                .flatMap(this::dispatchOutsideCommand)
                .orElseGet(() -> Flux.<PartialBotApiMethod<?>>from(commandsFactory.getHelpCommand().process(context))
                    .doOnNext(messageExecutor::execute));
        }
        log.debug("Dispatching command '{}' in chat {} (state={})", context.getCommand(), context.getChatId(), context.getState());
        Mono<Boolean> authorized = permitCommands.contains(context.getCommand())
                ? Mono.just(true)
                : authInterceptor.intercept(context);
        return authorized
            .<PartialBotApiMethod<?>>flatMapMany(allowed -> {
                if (!allowed) {
                    var from = context.getCommandUpdate().getFrom();
                    var username = from != null ? from.getUserName() : "unknown";
                    log.debug("Auth rejected for command '{}' in chat {} (user={})", context.getCommand(), context.getChatId(), username);
                    return Flux.error(new BotAuthException(context, "User " + username + " is unauthorized to use bot."));
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

    private Optional<Flux<PartialBotApiMethod<?>>> dispatchOutsideCommand(UpdateWrapper update) {
        var contactDispatch = update.getContact()
            .flatMap(contact -> contactHandlers.stream()
                .filter(handler -> handler.supports(contact))
                .findFirst()
                .map(handler -> dispatchBare(update, "contact " + contact.getUserId(),
                    bareContext -> handler.handle(bareContext, contact))));
        if (contactDispatch.isPresent()) {
            return contactDispatch;
        }
        var documentDispatch = update.getDocument()
            .flatMap(document -> documentHandlers.stream()
                .filter(handler -> handler.supports(document))
                .findFirst()
                .map(handler -> dispatchBare(update, "document '" + document.getFileName() + "'",
                    bareContext -> handler.handle(bareContext, document))));
        if (documentDispatch.isPresent()) {
            return documentDispatch;
        }
        return messageText(update)
            .flatMap(text -> textHandlers.stream()
                .findFirst()
                .map(handler -> dispatchBare(update, "text", bareContext -> handler.handle(bareContext, text))));
    }

    private Flux<PartialBotApiMethod<?>> dispatchBare(UpdateWrapper update, String description,
                                                     Function<CommandContext, Publisher<PartialBotApiMethod<?>>> handling) {
        var bareContext = CommandContext.forUpdate(update);
        log.debug("Dispatching {} in chat {}", description, bareContext.getChatId());
        return authInterceptor.intercept(bareContext)
            .<PartialBotApiMethod<?>>flatMapMany(authorized -> {
                if (!authorized) {
                    var from = update.getFrom();
                    var username = from != null ? from.getUserName() : "unknown";
                    return Flux.error(new BotAuthException(bareContext, "User " + username + " is unauthorized to use bot."));
                }
                return handling.apply(bareContext);
            })
            .onErrorMap(error -> error instanceof BotCommandException || error instanceof BotAuthException
                ? error
                : new BotCommandException(bareContext, error))
            .doOnNext(messageExecutor::execute);
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
    private record DispatchOutcome(CommandContext context, List<PartialBotApiMethod<?>> results) {
    }
}
