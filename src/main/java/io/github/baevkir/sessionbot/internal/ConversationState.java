package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.CommandContext;
import io.github.baevkir.sessionbot.DynamicParameters;
import io.github.baevkir.sessionbot.UpdateWrapper;
import org.springframework.util.Assert;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The library's mutable side of a {@link CommandContext}: one chat's fold state and its
 * open &rarr; progress &rarr; close lifecycle. A chat's updates are folded strictly one at a time, so
 * plain collections suffice.
 */
public final class ConversationState implements CommandContext {

    private final UpdateWrapper openingUpdate;
    private final boolean command;
    private final List<UpdateWrapper> updates = new ArrayList<>();
    private final List<String> answers = new ArrayList<>();
    private final List<Message> questionMessages = new ArrayList<>();
    private ContextState state = ContextState.open;
    private boolean pendingAnswersCommitted;

    private ConversationState(UpdateWrapper openingUpdate, boolean command) {
        this.openingUpdate = openingUpdate;
        this.command = command;
    }

    /** The seed of a chat's fold, before any update arrived. */
    public static ConversationState empty() {
        return new ConversationState(null, false);
    }

    public static ConversationState forCommand(UpdateWrapper commandUpdate) {
        Assert.isTrue(commandUpdate.isCommand(), "Context should be created only for command.");
        var conversation = new ConversationState(commandUpdate, true);
        conversation.answers.addAll(commandUpdate.getAnswers());
        return conversation;
    }

    /** A context for one update outside any command: plain text, a document, a contact, a stray button tap. */
    public static ConversationState forBareUpdate(UpdateWrapper update) {
        Assert.isTrue(!update.isCommand(), "A command opens a command context");
        var conversation = new ConversationState(update, false);
        conversation.updates.add(update);
        return conversation;
    }

    @Override
    public String getChatId() {
        return openingUpdate == null ? null : openingUpdate.getChatId();
    }

    @Override
    public User getUser() {
        return openingUpdate == null ? null : openingUpdate.getFrom();
    }

    @Override
    public String getCommand() {
        return command ? openingUpdate.getCommand() : null;
    }

    @Override
    public List<String> getAnswers() {
        var result = new ArrayList<>(answers);
        result.addAll(getPendingArguments());
        return Collections.unmodifiableList(result);
    }

    @Override
    public UpdateWrapper getCommandUpdate() {
        return openingUpdate;
    }

    @Override
    public Optional<UpdateWrapper> getCurrentUpdate() {
        return updates.isEmpty() ? Optional.empty() : Optional.of(updates.getLast());
    }

    @Override
    public Optional<MaybeInaccessibleMessage> getCallbackMessage() {
        return getCurrentUpdate().flatMap(UpdateWrapper::getCallbackMessage)
            .or(() -> Optional.ofNullable(openingUpdate).flatMap(UpdateWrapper::getCallbackMessage));
    }

    @Override
    public DynamicParameters getDynamicParams() {
        return getCurrentUpdate().or(() -> Optional.ofNullable(openingUpdate))
            .map(UpdateWrapper::getDynamicParams)
            .orElseGet(DynamicParameters::empty);
    }

    public boolean hasCommand() {
        return command;
    }

    public ContextState getState() {
        return state;
    }

    public ConversationState startProgress() {
        state = ContextState.progress;
        return this;
    }

    public ConversationState close() {
        state = ContextState.close;
        return this;
    }

    /**
     * Folds {@link #getPendingArguments()} into the committed answers: the pending answers themselves
     * when there are any, else a single empty answer when {@code canSkipAnswer} allows skipping.
     * Afterward {@link #getPendingArguments()} returns an empty list until the next {@link #addUpdate}.
     */
    public ConversationState commitPendingAnswers(boolean canSkipAnswer) {
        var pending = getPendingArguments();
        if (pending.isEmpty() && canSkipAnswer) {
            answers.add("");
        } else {
            answers.addAll(pending);
        }
        pendingAnswersCommitted = true;
        return this;
    }

    public ConversationState addUpdate(UpdateWrapper update) {
        Assert.isTrue(!update.isCommand(), "Command should create new context");
        updates.add(update);
        pendingAnswersCommitted = false;
        return this;
    }

    public ConversationState addQuestionMessage(Message message) {
        Objects.requireNonNull(message, "Message is null");
        questionMessages.add(message);
        return this;
    }

    public List<Message> getQuestionMessages() {
        return Collections.unmodifiableList(questionMessages);
    }

    public List<UpdateWrapper> getUpdates() {
        return Collections.unmodifiableList(updates);
    }

    /**
     * The answers the latest update carries, not yet folded into {@link #getAnswers()}'s committed part.
     * Empty once {@link #commitPendingAnswers} has run for this update, until the next {@link #addUpdate}.
     */
    public List<String> getPendingArguments() {
        if (pendingAnswersCommitted) {
            return List.of();
        }
        return getCurrentUpdate().map(UpdateWrapper::getAnswers).orElse(List.of());
    }
}
