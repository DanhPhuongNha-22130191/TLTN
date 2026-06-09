package secretchat.chatservice.infrastructure.persistence.mapper;



import org.springframework.stereotype.Component;
import secretchat.chatservice.domain.model.Message;
import secretchat.chatservice.infrastructure.persistence.entity.MessageEntity;

@Component
public class MessageMapper {

    public MessageEntity toEntity(Message domain) {
        return MessageEntity.builder()
                .id(domain.getId())
                .conversationId(domain.getConversationId())
                .senderId(domain.getSenderId())
                .content(domain.getContent())
                .fileUrl(domain.getFileUrl())
                .fileName(domain.getFileName())
                .fileSize(domain.getFileSize())
                .fileType(domain.getFileType())
                .messageType(domain.getMessageType().name())
                .replyToId(domain.getReplyToId())
                .isDeleted(domain.isDeleted())
                .deletedAt(domain.getDeletedAt())
                .deletedBy(domain.getDeletedBy())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .deletedForUsers(domain.getDeletedForUsers())
                .status(domain.getStatus().name())
                .starred(domain.isStarred())
                .pinned(domain.isPinned())
                .editedAt(domain.getEditedAt())
                .build();
    }

    public Message toDomain(MessageEntity entity) {
        return Message.builder()
                .id(entity.getId())
                .conversationId(entity.getConversationId())
                .senderId(entity.getSenderId())
                .content(entity.getContent())
                .fileUrl(entity.getFileUrl())
                .fileName(entity.getFileName())
                .fileSize(entity.getFileSize())
                .fileType(entity.getFileType())
                .messageType(secretchat.chatservice.domain.enums.MessageType.valueOf(entity.getMessageType()))
                .replyToId(entity.getReplyToId())
                .isDeleted(entity.isDeleted())
                .deletedAt(entity.getDeletedAt())
                .deletedBy(entity.getDeletedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .deletedForUsers(entity.getDeletedForUsers())
                .status(entity.getStatus() == null
                        ? secretchat.chatservice.domain.enums.MessageStatus.SENT
                        : secretchat.chatservice.domain.enums.MessageStatus.valueOf(entity.getStatus()))
                .starred(entity.isStarred())
                .pinned(entity.isPinned())
                .editedAt(entity.getEditedAt())
                .build();
    }
}
