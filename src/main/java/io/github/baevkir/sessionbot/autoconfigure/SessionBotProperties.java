package io.github.baevkir.sessionbot.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "sessionbot.telegram")
public class SessionBotProperties {
    /** The bot's Telegram API token; together with {@code bot-username} it activates the bot. */
    private String token;
    /**
     * The bot's Telegram user name, without the leading {@code @}; group commands addressed to another bot
     * ({@code /order@OtherBot}) are ignored.
     */
    private String botUsername;
    /** Bot-wide language tag for built-in labels (e.g. en, uk, ru). */
    private String language = "en";
    /** Idle period after which an inactive chat's update stream is released. */
    private Duration chatIdleTtl = Duration.ofMinutes(30);
    /** Maximum number of chats processed concurrently (per-chat fan-out concurrency). */
    private int maxConcurrentChats = 256;
    /**
     * Commands that run without consulting the {@code AuthInterceptor} — typically an entry point such
     * as {@code start} that must run before the caller can be recognized. Empty by default.
     */
    private List<String> permitCommands = List.of();
}
