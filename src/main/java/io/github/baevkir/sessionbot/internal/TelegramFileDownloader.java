package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.FileDownloader;
import io.github.baevkir.sessionbot.FileTooLargeException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * The default {@link FileDownloader}: {@code getFile}, then a capped stream, on a client meant for files only. A
 * {@code getFile} that Telegram leaves unanswered is tried once more on a new connection: either Telegram has
 * finished copying the file by then, or the first connection was one the network had silently dropped.
 */
@Slf4j
public class TelegramFileDownloader implements FileDownloader {

    private final TelegramClient client;
    private final Runnable dropIdleConnections;

    /** For a client whose connections are not this downloader's to manage: a retry may reuse one. */
    public TelegramFileDownloader(TelegramClient client) {
        this(client, () -> { });
    }

    /** {@code dropIdleConnections} runs before the retry, so it goes out on a new connection. */
    public TelegramFileDownloader(TelegramClient client, Runnable dropIdleConnections) {
        this.client = client;
        this.dropIdleConnections = dropIdleConnections;
    }

    /**
     * Waits {@code readTimeout} for Telegram, and keeps an idle connection for one minute: long enough for a
     * photo's {@code getFile} and download, or an album's photos, to share one handshake, and short enough that
     * the network has not silently dropped it before the next use. A call Telegram leaves unanswered is logged with
     * whether its connection was new or reused.
     */
    public static OkHttpClient httpClient(Duration readTimeout) {
        return new OkHttpClient.Builder()
                .readTimeout(readTimeout)
                .connectionPool(new ConnectionPool(5, 1, TimeUnit.MINUTES))
                .eventListenerFactory(call -> new TimeoutLog())
                .build();
    }

    @Override
    public Mono<byte[]> download(String fileId, long maxBytes) {
        return Mono.fromCallable(() -> read(fileId, maxBytes))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private byte[] read(String fileId, long maxBytes) throws TelegramApiException, IOException {
        File file = getFile(fileId);
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

    private File getFile(String fileId) throws TelegramApiException {
        long started = System.nanoTime();
        try {
            return client.execute(GetFile.builder().fileId(fileId).build());
        } catch (TelegramApiException ex) {
            if (!timedOut(ex)) {
                throw ex;
            }
            dropIdleConnections.run();
            File file = client.execute(GetFile.builder().fileId(fileId).build());
            log.info("getFile answered on the second try, {} ms after the first began", millisSince(started));
            return file;
        }
    }

    private static boolean timedOut(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedIOException) {
                return true;
            }
        }
        return false;
    }

    private static long millisSince(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    /** One per call: logs a call that timed out, never its URL, which carries the bot token. */
    private static final class TimeoutLog extends EventListener {

        private long started;
        private boolean newConnection;

        @Override
        public void callStart(Call call) {
            started = System.nanoTime();
        }

        @Override
        public void connectStart(Call call, InetSocketAddress address, Proxy proxy) {
            newConnection = true;
        }

        @Override
        public void callFailed(Call call, IOException ioe) {
            if (timedOut(ioe)) {
                log.warn("Telegram did not answer {} within {} ms on a {} connection", what(call),
                        millisSince(started), newConnection ? "new" : "reused");
            }
        }

        private static String what(Call call) {
            var segments = call.request().url().pathSegments();
            return "file".equals(segments.getFirst()) ? "a file download" : segments.getLast();
        }
    }
}
