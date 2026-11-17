package io.github.baevkir.sessionbot;

import java.io.IOException;

/** A file over the cap a {@link FileDownloader} was given. */
public class FileTooLargeException extends IOException {

    private final long maxBytes;

    public FileTooLargeException(long maxBytes) {
        super("The file is over " + maxBytes + " bytes");
        this.maxBytes = maxBytes;
    }

    public long getMaxBytes() {
        return maxBytes;
    }
}
