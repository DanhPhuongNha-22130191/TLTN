package secretchat.chatservice.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import secretchat.chatservice.application.port.in.MessageUseCase;
import secretchat.chatservice.application.port.out.MessageRepositoryPort;
import secretchat.chatservice.application.usecase.command.SendMessageCommand;
import secretchat.chatservice.domain.enums.MessageType;
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
                .build();

        messageRepositoryPort.save(updatedMessage);
    }
}
