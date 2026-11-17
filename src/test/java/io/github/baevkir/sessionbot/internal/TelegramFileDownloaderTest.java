package io.github.baevkir.sessionbot.internal;

import io.github.baevkir.sessionbot.FileTooLargeException;
import io.github.baevkir.sessionbot.autoconfigure.SessionBotProperties;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import reactor.test.StepVerifier;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A file is read into memory, and never past the cap, whether or not Telegram gives its size. */
class TelegramFileDownloaderTest {

    private static final long MAX = 10;

    private final TelegramClient client = mock(TelegramClient.class);
    private final TelegramFileDownloader downloader = new TelegramFileDownloader(client);

    @Test
    void aFileWithinTheCapIsReadIntoMemory() throws Exception {
        File file = file(5L);
        when(client.execute(any(GetFile.class))).thenReturn(file);
        when(client.downloadFileAsStream(file)).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5}));

        StepVerifier.create(downloader.download("file-1", MAX))
                .assertNext(bytes -> assertThat(bytes).containsExactly(1, 2, 3, 4, 5))
                .verifyComplete();
    }

    @Test
    void aFileTelegramSaysIsOverTheCapIsNeverStreamed() throws Exception {
        File file = file(MAX + 1);
        when(client.execute(any(GetFile.class))).thenReturn(file);

        StepVerifier.create(downloader.download("file-1", MAX))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(FileTooLargeException.class,
                                tooLarge -> assertThat(tooLarge.getMaxBytes()).isEqualTo(MAX)))
                .verify();

        verify(client, never()).downloadFileAsStream(any(File.class));
    }

    @Test
    void aStreamPastTheCapIsRefusedWhenTelegramGaveNoSize() throws Exception {
        File file = file(null);
        when(client.execute(any(GetFile.class))).thenReturn(file);
        when(client.downloadFileAsStream(file)).thenReturn(new ByteArrayInputStream(new byte[(int) MAX + 5]));

        StepVerifier.create(downloader.download("file-1", MAX))
                .expectError(FileTooLargeException.class)
                .verify();
    }

    @Test
    void theFileClientWaitsLongerThanTheMessageClientsTenSeconds() {
        OkHttpClient files = TelegramFileDownloader.httpClient(new SessionBotProperties().getFileTimeout());

        assertThat(files.readTimeoutMillis()).isEqualTo(30_000);
    }

    private static File file(Long size) {
        File file = new File();
        file.setFileId("file-1");
        file.setFileSize(size);
        return file;
    }
}
