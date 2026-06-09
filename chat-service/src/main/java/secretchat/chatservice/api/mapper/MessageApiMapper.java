package secretchat.chatservice.api.mapper;

import secretchat.chatservice.api.request.SendMessageRequest;
import secretchat.chatservice.api.response.MessageResponse;
import secretchat.chatservice.application.usecase.command.SendMessageCommand;
import secretchat.chatservice.domain.model.Message;

public final class MessageApiMapper {

    private MessageApiMapper() {
    }

    public static SendMessageCommand toCommand(SendMessageRequest request) {
        if (request == null) {
            return null;
        }
        return SendMessageCommand.builder()
                .conversationId(request.getConversationId())
                .senderId(request.getSenderId())
                .content(request.getContent())
                .fileUrl(request.getFileUrl())
                .fileName(request.getFileName())
                .fileSize(request.getFileSize())
                .fileType(request.getFileType())
                .messageType(request.getMessageType())
                .replyToId(request.getReplyToId())
                .build();
    }

    public static MessageResponse toResponse(Message message) {
        if (message == null) {
            return null;
        }
        return MessageResponse.builder()
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
                .build();
    }
}
