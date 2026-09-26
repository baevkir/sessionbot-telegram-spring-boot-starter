package com.kb.sessionbot;

import com.kb.sessionbot.errors.handler.ErrorHandlerFactory;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.methods.SetMyProfilePhoto;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.business.SetBusinessAccountProfilePhoto;
import org.telegram.telegrambots.meta.api.methods.groupadministration.SetChatPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendLivePhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendPaidMedia;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendSticker;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.methods.send.SendVideoNote;
import org.telegram.telegrambots.meta.api.methods.send.SendVoice;
import org.telegram.telegrambots.meta.api.methods.stickers.AddStickerToSet;
import org.telegram.telegrambots.meta.api.methods.stickers.CreateNewStickerSet;
import org.telegram.telegrambots.meta.api.methods.stickers.ReplaceStickerInSet;
import org.telegram.telegrambots.meta.api.methods.stickers.UploadStickerFile;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.Serializable;

/**
 * Default {@link MessageExecutor} dispatching over a thread-safe {@link TelegramClient}: every
 * multipart method gets its typed {@code execute} overload, everything else the generic one.
 * {@code SetWebhook} is deliberately unsupported, since the bot runs on long polling.
 */
@Slf4j
public class TelegramClientMessageExecutor implements MessageExecutor {

    private final TelegramClient telegramClient;
    private final ErrorHandlerFactory errorHandler;

    public TelegramClientMessageExecutor(TelegramClient telegramClient, ErrorHandlerFactory errorHandler) {
        this.telegramClient = telegramClient;
        this.errorHandler = errorHandler;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Serializable> T execute(PartialBotApiMethod<T> message) {
        try {
            log.debug("Executing {}", message.getClass().getSimpleName());
            return switch (message) {
                case BotApiMethod<?> botApiMethod -> telegramClient.execute((BotApiMethod<T>) botApiMethod);
                case SendPhoto sendPhoto -> (T) telegramClient.execute(sendPhoto);
                case SendDocument sendDocument -> (T) telegramClient.execute(sendDocument);
                case SendVideo sendVideo -> (T) telegramClient.execute(sendVideo);
                case SendAudio sendAudio -> (T) telegramClient.execute(sendAudio);
                case SendVoice sendVoice -> (T) telegramClient.execute(sendVoice);
                case SendSticker sendSticker -> (T) telegramClient.execute(sendSticker);
                case SendAnimation sendAnimation -> (T) telegramClient.execute(sendAnimation);
                case SendVideoNote sendVideoNote -> (T) telegramClient.execute(sendVideoNote);
                case SendLivePhoto sendLivePhoto -> (T) telegramClient.execute(sendLivePhoto);
                case SendMediaGroup sendMediaGroup -> (T) telegramClient.execute(sendMediaGroup);
                case SendPaidMedia sendPaidMedia -> (T) telegramClient.execute(sendPaidMedia);
                case EditMessageMedia editMessageMedia -> (T) telegramClient.execute(editMessageMedia);
                case SetChatPhoto setChatPhoto -> (T) telegramClient.execute(setChatPhoto);
                case SetMyProfilePhoto setMyProfilePhoto -> (T) telegramClient.execute(setMyProfilePhoto);
                case SetBusinessAccountProfilePhoto setProfilePhoto -> (T) telegramClient.execute(setProfilePhoto);
                case CreateNewStickerSet createNewStickerSet -> (T) telegramClient.execute(createNewStickerSet);
                case AddStickerToSet addStickerToSet -> (T) telegramClient.execute(addStickerToSet);
                case ReplaceStickerInSet replaceStickerInSet -> (T) telegramClient.execute(replaceStickerInSet);
                case UploadStickerFile uploadStickerFile -> (T) telegramClient.execute(uploadStickerFile);
                default -> {
                    log.warn("Unsupported message type {}; routing through error handler", message.getClass().getSimpleName());
                    errorHandler.handle(new UnsupportedOperationException(
                        "Message type " + message.getClass().getSimpleName() + " is not supported"))
                        .subscribe(this::execute);
                    yield null;
                }
            };
        } catch (TelegramApiException e) {
            log.error("Cannot execute message in chat (type={})", message.getClass().getSimpleName(), e);
            return null;
        }
    }
}