package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.FileDownloader;
import io.github.baevkir.sessionbot.FileTooLargeException;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** The default {@link FileDownloader}: {@code getFile}, then a capped stream, on a client meant for files only. */
public class TelegramFileDownloader implements FileDownloader {

    private final TelegramClient client;

    public TelegramFileDownloader(TelegramClient client) {
        this.client = client;
    }

    /**
     * Waits {@code readTimeout} for Telegram, and keeps an idle connection for one minute: long enough for a
     * photo's {@code getFile} and download, or an album's photos, to share one handshake, and short enough that
     * the network has not silently dropped it before the next use.
     */
    public static OkHttpClient httpClient(Duration readTimeout) {
        return new OkHttpClient.Builder()
                .readTimeout(readTimeout)
                .connectionPool(new ConnectionPool(5, 1, TimeUnit.MINUTES))
                .build();
    }

    @Override
    public Mono<byte[]> download(String fileId, long maxBytes) {
        return Mono.fromCallable(() -> read(fileId, maxBytes))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private byte[] read(String fileId, long maxBytes) throws TelegramApiException, IOException {
        File file = client.execute(GetFile.builder().fileId(fileId).build());
        // The size an update carries is optional on Telegram's side; the resolved File carries the real one,
        // so it is checked before anything is streamed, and the stream is capped for a file that has none.
        if (file.getFileSize() != null && file.getFileSize() > maxBytes) {
            throw new FileTooLargeException(maxBytes);
        }
        try (InputStream stream = client.downloadFileAsStream(file)) {
            byte[] bytes = stream.readNBytes(Math.toIntExact(maxBytes + 1));
            if (bytes.length > maxBytes) {
                throw new FileTooLargeException(maxBytes);
            }
            return bytes;
        }
    }
}
