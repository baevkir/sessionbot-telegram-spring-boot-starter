package io.github.baevkir.sessionbot;

import reactor.core.publisher.Mono;

/**
 * Reads a file someone sent the bot (a document's or a photo's {@code fileId}) into memory. Telegram answers
 * {@code getFile} only once it has copied the file to its Bot API server, which can outlast the 10-second read
 * timeout of the client that sends messages, so the default implementation has a client of its own that waits
 * {@code sessionbot.telegram.file-timeout}.
 */
public interface FileDownloader {

    /**
     * The file's bytes, or a {@link FileTooLargeException} when the file is over {@code maxBytes}, whether or not
     * Telegram reports its size; nothing past the cap is read. The download blocks, so it runs off the
     * subscriber's thread.
     */
    Mono<byte[]> download(String fileId, long maxBytes);
}
