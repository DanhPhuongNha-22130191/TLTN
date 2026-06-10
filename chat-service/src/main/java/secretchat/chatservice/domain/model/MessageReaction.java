package secretchat.chatservice.domain.model;

public record MessageReaction(Long messageId, String userId, String emoji) {
}
