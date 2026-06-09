package secretchat.chatservice.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import secretchat.chatservice.domain.enums.ConversationType;
import secretchat.chatservice.domain.model.Conversation;
import secretchat.chatservice.infrastructure.persistence.entity.ConversationEntity;

@Component
public class ConversationMapper {

    public ConversationEntity toEntity(Conversation domain) {
        if (domain == null) {
            return null;
        }
        return ConversationEntity.builder()
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

    public Conversation toDomain(ConversationEntity entity) {
        if (entity == null) {
            return null;
        }
        return Conversation.builder()
                .id(entity.getId())
                .type(ConversationType.valueOf(entity.getType()))
                .senderId(entity.getSenderId())
                .receiverId(entity.getReceiverId())
                .groupId(entity.getGroupId())
                .lastMessageId(entity.getLastMessageId())
                .lastMessageAt(entity.getLastMessageAt())
                .unreadCount(entity.getUnreadCount())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
