package secretchat.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class MessageTimeFormatter {
    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("HH:mm");

    private MessageTimeFormatter() {
    }

    public static String format(String value) {
        if (value == null || value.isBlank()) return "";
        String trimmed = value.trim();
        try {
            return OffsetDateTime.parse(trimmed)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .format(DISPLAY_TIME);
        } catch (DateTimeParseException ignored) {
            // Backend currently serializes LocalDateTime without an offset while running in UTC.
        }
        try {
            return LocalDateTime.parse(trimmed)
                    .atOffset(ZoneOffset.UTC)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .format(DISPLAY_TIME);
        } catch (DateTimeParseException ignored) {
            // Some transient UI entries already store display-only HH:mm strings.
        }
        try {
            return Instant.parse(trimmed)
                    .atZone(ZoneId.systemDefault())
                    .format(DISPLAY_TIME);
        } catch (DateTimeParseException ignored) {
            return trimmed.length() >= 5 ? trimmed.substring(0, 5) : trimmed;
        }
    }
}
