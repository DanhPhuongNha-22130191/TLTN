package secretchat.chatservice.domain.model;


import secretchat.chatservice.domain.enums.MessageType;
import secretchat.chatservice.domain.enums.MessageStatus;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Pure domain entity - không phụ thuộc framework nào
 */
public class Message {

    private final Long id;
    private final Long conversationId;
    private final String senderId;
    private final String content;
    private final String fileUrl;
    private final String fileName;
    private final Long fileSize;
    private final String fileType;
    private final MessageType messageType;
    private final Long replyToId;
    private final boolean isDeleted;
    private final LocalDateTime deletedAt;
    private final String deletedBy;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final String deletedForUsers;
    private final MessageStatus status;
    private final boolean starred;
    private final boolean pinned;
    private final LocalDateTime editedAt;

    // Private constructor - bắt buộc dùng Builder
    private Message(Builder builder) {
        this.id = builder.id;
        this.conversationId = builder.conversationId;
        this.senderId = builder.senderId;
        this.content = builder.content;
        this.fileUrl = builder.fileUrl;
        this.fileName = builder.fileName;
        this.fileSize = builder.fileSize;
        this.fileType = builder.fileType;
        this.messageType = builder.messageType;
        this.replyToId = builder.replyToId;
        this.isDeleted = builder.isDeleted;
        this.deletedAt = builder.deletedAt;
        this.deletedBy = builder.deletedBy;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
        this.deletedForUsers = builder.deletedForUsers;
        this.status = builder.status;
        this.starred = builder.starred;
        this.pinned = builder.pinned;
        this.editedAt = builder.editedAt;
    }

    // Getters (không có setters - immutable)
    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public String getSenderId() { return senderId; }
    public String getContent() { return content; }
    public String getFileUrl() { return fileUrl; }
    public String getFileName() { return fileName; }
    public Long getFileSize() { return fileSize; }
    public String getFileType() { return fileType; }
    public MessageType getMessageType() { return messageType; }
    public Long getReplyToId() { return replyToId; }
    public boolean isDeleted() { return isDeleted; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public String getDeletedBy() { return deletedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getDeletedForUsers() { return deletedForUsers; }
    public MessageStatus getStatus() { return status; }
    public boolean isStarred() { return starred; }
    public boolean isPinned() { return pinned; }
    public LocalDateTime getEditedAt() { return editedAt; }

    // Business methods
    public boolean hasContent() {
        return content != null && !content.trim().isEmpty();
    }

    public boolean hasFile() {
        return fileUrl != null && !fileUrl.isEmpty();
    }

    public boolean isReply() {
        return replyToId != null;
    }

    public void validate() {
        if (conversationId == null) {
            throw new IllegalStateException("Conversation ID is required");
        }
        if (senderId == null) {
            throw new IllegalStateException("Sender ID is required");
        }
        if (messageType == null) {
            throw new IllegalStateException("Message type is required");
        }
        if (messageType == MessageType.TEXT && !hasContent()) {
            throw new IllegalStateException("Text message must have content");
        }
        if ((messageType == MessageType.FILE ||
                messageType == MessageType.IMAGE ||
                messageType == MessageType.VIDEO) && !hasFile()) {
            throw new IllegalStateException("File message must have file URL");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return Objects.equals(id, message.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Message{" +
                "id=" + id +
                ", conversationId=" + conversationId +
                ", senderId=" + senderId +
                ", messageType=" + messageType +
                ", hasContent=" + hasContent() +
                ", hasFile=" + hasFile() +
                ", createdAt=" + createdAt +
                '}';
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private Long conversationId;
        private String senderId;
        private String content;
        private String fileUrl;
        private String fileName;
        private Long fileSize;
        private String fileType;
        private MessageType messageType = MessageType.TEXT;
        private Long replyToId;
        private boolean isDeleted = false;
        private LocalDateTime deletedAt;
        private String deletedBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String deletedForUsers;
        private MessageStatus status = MessageStatus.SENT;
        private boolean starred;
        private boolean pinned;
        private LocalDateTime editedAt;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder conversationId(Long conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder senderId(String senderId) {
            this.senderId = senderId;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder fileUrl(String fileUrl) {
            this.fileUrl = fileUrl;
            return this;
        }

        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder fileSize(Long fileSize) {
            this.fileSize = fileSize;
            return this;
        }

        public Builder fileType(String fileType) {
            this.fileType = fileType;
            return this;
        }

        public Builder messageType(MessageType messageType) {
            this.messageType = messageType;
            return this;
        }

        public Builder replyToId(Long replyToId) {
            this.replyToId = replyToId;
            return this;
        }

        public Builder isDeleted(boolean isDeleted) {
            this.isDeleted = isDeleted;
            return this;
        }

        public Builder deletedAt(LocalDateTime deletedAt) {
            this.deletedAt = deletedAt;
            return this;
        }

        public Builder deletedBy(String deletedBy) {
            this.deletedBy = deletedBy;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder deletedForUsers(String deletedForUsers) {
            this.deletedForUsers = deletedForUsers;
            return this;
        }

        public Builder status(MessageStatus status) {
            this.status = status;
            return this;
        }

        public Builder starred(boolean starred) {
            this.starred = starred;
            return this;
        }

        public Builder pinned(boolean pinned) {
            this.pinned = pinned;
            return this;
        }

        public Builder editedAt(LocalDateTime editedAt) {
            this.editedAt = editedAt;
            return this;
        }

        public Message build() {
            Message message = new Message(this);
            message.validate();
            return message;
        }
    }


}
