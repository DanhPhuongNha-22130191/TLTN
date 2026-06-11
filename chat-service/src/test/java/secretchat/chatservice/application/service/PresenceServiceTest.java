package secretchat.chatservice.application.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresenceServiceTest {

    @Test
    void heartbeatStoresPresenceWithConfiguredTtl() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        PresenceService service = new PresenceService(redis, Duration.ofSeconds(75));

        PresenceService.PresenceStatus status = service.heartbeat("user-1");

        ArgumentCaptor<String> timestamp = ArgumentCaptor.forClass(String.class);
        verify(values).set(
                org.mockito.ArgumentMatchers.eq("presence:user:user-1"),
                timestamp.capture(),
                org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(75)));
        assertTrue(status.online());
        assertEquals("user-1", status.userId());
        assertEquals(timestamp.getValue(), status.lastSeen().toString());
    }

    @Test
    void missingRedisKeyIsOffline() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);
        PresenceService service = new PresenceService(redis, Duration.ofSeconds(75));

        PresenceService.PresenceStatus status = service.getStatus("user-2");

        assertFalse(status.online());
        assertEquals("user-2", status.userId());
    }
}
