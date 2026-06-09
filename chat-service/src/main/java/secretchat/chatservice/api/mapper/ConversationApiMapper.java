package secretchat.chatservice.api.mapper;

import secretchat.chatservice.api.response.ConversationResponse;
import secretchat.chatservice.domain.model.Conversation;

public final class ConversationApiMapper {

    private ConversationApiMapper() {}

    public static ConversationResponse toResponse(Conversation domain) {
        if (domain == null) {
            return null;
        }
        return ConversationResponse.builder()
                .id(domain.getId())
                .type(domain.getType().name())
                .senderId(domain.getSenderId())
                .receiverId(domain.getReceiverId())
                .groupId(domain.getGroupId())
                .lastMessageId(domain.getLastMessageId())
                .lastMessageAt(domain.getLastMessageAt())
                .unreadCount(domain.getUnreadCount())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}
