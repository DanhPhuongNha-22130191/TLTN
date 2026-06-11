package secretchat.chatservice.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PresenceService {
    private static final String KEY_PREFIX = "presence:user:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public PresenceService(
            StringRedisTemplate redisTemplate,
            @Value("${app.presence.ttl:75s}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    public PresenceStatus heartbeat(String userId) {
        Instant lastSeen = Instant.now();
        redisTemplate.opsForValue().set(key(userId), lastSeen.toString(), ttl);
        return new PresenceStatus(userId, true, lastSeen);
    }

    public PresenceStatus getStatus(String userId) {
        String value = redisTemplate.opsForValue().get(key(userId));
        if (value == null) {
            return new PresenceStatus(userId, false, null);
        }
        return new PresenceStatus(userId, true, parseInstant(value));
    }

    public Map<String, PresenceStatus> getStatuses(Collection<String> userIds) {
        Map<String, PresenceStatus> statuses = new LinkedHashMap<>();
        userIds.forEach(userId -> statuses.put(userId, getStatus(userId)));
        return statuses;
    }

    private String key(String userId) {
        return KEY_PREFIX + userId;
    }

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    public record PresenceStatus(String userId, boolean online, Instant lastSeen) {}
}
