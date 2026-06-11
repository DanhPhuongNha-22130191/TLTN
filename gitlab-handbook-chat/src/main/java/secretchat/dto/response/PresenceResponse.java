package secretchat.dto.response;

public record PresenceResponse(String userId, boolean online, String lastSeen) {}
