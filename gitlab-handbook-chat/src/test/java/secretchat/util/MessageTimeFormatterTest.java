package secretchat.util;

import org.junit.jupiter.api.Test;

import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageTimeFormatterTest {

    @Test
    void convertsBackendUtcLocalDateTimeToSystemTimeZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Bangkok"));

            assertEquals("12:27", MessageTimeFormatter.format("2026-06-26T05:27:30"));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void keepsDisplayOnlyTimeValues() {
        assertEquals("05:27", MessageTimeFormatter.format("05:27"));
    }
}
