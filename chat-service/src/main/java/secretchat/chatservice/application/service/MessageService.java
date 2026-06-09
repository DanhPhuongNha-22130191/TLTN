package secretchat.chatservice.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import secretchat.chatservice.application.port.in.MessageUseCase;
import secretchat.chatservice.application.port.out.MessageRepositoryPort;
import secretchat.chatservice.application.usecase.command.SendMessageCommand;
import secretchat.chatservice.domain.enums.MessageType;
import secretchat.chatservice.domain.enums.MessageStatus;
import secretchat.chatservice.domain.model.Message;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService implements MessageUseCase {

    private final MessageRepositoryPort messageRepositoryPort;

    @Override
    public Message sendMessage(SendMessageCommand command) {
        Message message = Message.builder()
                .conversationId(command.getConversationId())
                .senderId(command.getSenderId())
                .content(command.getContent())
                .fileUrl(command.getFileUrl())
                .fileName(command.getFileName())
                .fileSize(command.getFileSize())
                .fileType(command.getFileType())
                .messageType(command.getMessageType() != null ? command.getMessageType() : MessageType.TEXT)
                .replyToId(command.getReplyToId())
                .isDeleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .status(MessageStatus.SENT)
                .build();
        return messageRepositoryPort.save(message);
    }

    @Override
    public List<Message> getChatHistory(Long conversationId) {
        return messageRepositoryPort.findByConversationId(conversationId);
    }

    @Override
    public Message getMessage(Long messageId) {
        return messageRepositoryPort.findById(messageId);
    }

    @Override
    public Message recallMessage(Long messageId, String userId) {
        Message message = messageRepositoryPort.findById(messageId);
        if (!message.getSenderId().equals(userId)) {
            throw new secretchat.chatservice.application.exception.BusinessException("You can only recall your own messages");
        }
        if (message.getCreatedAt().plusHours(24).isBefore(LocalDateTime.now())) {
            throw new secretchat.chatservice.application.exception.BusinessException("Message can only be recalled within 24 hours");
        }

        Message updatedMessage = Message.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .content(message.getContent())
                .fileUrl(message.getFileUrl())
                .fileName(message.getFileName())
                .fileSize(message.getFileSize())
                .fileType(message.getFileType())
                .messageType(message.getMessageType())
                .replyToId(message.getReplyToId())
                .isDeleted(true)
                .deletedAt(LocalDateTime.now())
                .deletedBy(userId)
                .createdAt(message.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .deletedForUsers(message.getDeletedForUsers())
                .status(message.getStatus())
                .starred(message.isStarred())
                .pinned(message.isPinned())
                .editedAt(message.getEditedAt())
                .build();

        return messageRepositoryPort.save(updatedMessage);
    }

    @Override
    public void deleteMessageForUser(Long messageId, String userId) {
        Message message = messageRepositoryPort.findById(messageId);
        
        String currentDeletedFor = message.getDeletedForUsers() == null ? "" : message.getDeletedForUsers();
        if (!currentDeletedFor.contains(userId)) {
            currentDeletedFor = currentDeletedFor.isEmpty() ? userId : currentDeletedFor + "," + userId;
        }

        Message updatedMessage = Message.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .content(message.getContent())
                .fileUrl(message.getFileUrl())
                .fileName(message.getFileName())
                .fileSize(message.getFileSize())
                .fileType(message.getFileType())
                .messageType(message.getMessageType())
                .replyToId(message.getReplyToId())
                .isDeleted(message.isDeleted())
                .deletedAt(message.getDeletedAt())
                .deletedBy(message.getDeletedBy())
                .createdAt(message.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .deletedForUsers(currentDeletedFor)
                .status(message.getStatus())
                .starred(message.isStarred())
                .pinned(message.isPinned())
                .editedAt(message.getEditedAt())
                .build();

        messageRepositoryPort.save(updatedMessage);
    }

    @Override
    public Message editMessage(Long messageId, String userId, String content) {
        Message message = messageRepositoryPort.findById(messageId);
        if (!message.getSenderId().equals(userId)) {
            throw new secretchat.chatservice.application.exception.BusinessException(
                    "You can only edit your own messages");
        }
        if (message.isDeleted() || message.getMessageType() != MessageType.TEXT) {
            throw new secretchat.chatservice.application.exception.BusinessException(
                    "Only active text messages can be edited");
        }
        if (content == null || content.isBlank()) {
            throw new secretchat.chatservice.application.exception.BusinessException(
                    "Message content is required");
        }
        return messageRepositoryPort.save(copy(message)
                .content(content.trim())
                .updatedAt(LocalDateTime.now())
                .editedAt(LocalDateTime.now())
                .build());
    }

    @Override
    public Message setStarred(Long messageId, boolean starred) {
        Message message = messageRepositoryPort.findById(messageId);
        return messageRepositoryPort.save(copy(message).starred(starred).updatedAt(LocalDateTime.now()).build());
    }

    @Override
    public Message setPinned(Long messageId, boolean pinned) {
        Message message = messageRepositoryPort.findById(messageId);
        return messageRepositoryPort.save(copy(message).pinned(pinned).updatedAt(LocalDateTime.now()).build());
    }

    @Override
    public Message updateStatus(Long messageId, String userId, MessageStatus status) {
        Message message = messageRepositoryPort.findById(messageId);
        if (message.getSenderId().equals(userId)) {
            return message;
        }
        if (status.ordinal() <= message.getStatus().ordinal()) {
            return message;
        }
        return messageRepositoryPort.save(copy(message).status(status).updatedAt(LocalDateTime.now()).build());
    }

    @Override
    public List<Message> getPinnedMessages(Long conversationId) {
        return messageRepositoryPort.findPinnedByConversationId(conversationId);
    }

    @Override
    public List<Message> getMessagesAround(Long messageId, int limit) {
        Message target = messageRepositoryPort.findById(messageId);
        List<Message> history = messageRepositoryPort.findByConversationId(target.getConversationId());
        int targetIndex = -1;
        for (int index = 0; index < history.size(); index++) {
            if (history.get(index).getId().equals(messageId)) {
                targetIndex = index;
                break;
            }
        }
        if (targetIndex < 0) return List.of(target);
        int radius = Math.max(1, Math.min(limit, 50));
        int from = Math.max(0, targetIndex - radius);
        int to = Math.min(history.size(), targetIndex + radius + 1);
        return history.subList(from, to);
    }

    @Override
    public List<Message> searchMessages(Long conversationId, String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return messageRepositoryPort.searchByConversationId(conversationId, query.trim());
    }

    private Message.Builder copy(Message message) {
        return Message.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .content(message.getContent())
                .fileUrl(message.getFileUrl())
                .fileName(message.getFileName())
                .fileSize(message.getFileSize())
                .fileType(message.getFileType())
                .messageType(message.getMessageType())
                .replyToId(message.getReplyToId())
                .isDeleted(message.isDeleted())
                .deletedAt(message.getDeletedAt())
                .deletedBy(message.getDeletedBy())
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .deletedForUsers(message.getDeletedForUsers())
                .status(message.getStatus())
                .starred(message.isStarred())
                .pinned(message.isPinned())
                .editedAt(message.getEditedAt());
    }
}
