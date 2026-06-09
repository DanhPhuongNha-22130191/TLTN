package secretchat.chatservice.domain.model;


import secretchat.chatservice.domain.enums.ConversationType;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Pure domain entity - không có annotations
 */
public class Conversation {

    private final Long id;
    private final ConversationType type;
    private final String senderId;
    private final String receiverId;
    private final Long groupId;
    private final Long lastMessageId;
    private final LocalDateTime lastMessageAt;
    private final int unreadCount;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private Conversation(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.senderId = builder.senderId;
        this.receiverId = builder.receiverId;
        this.groupId = builder.groupId;
        this.lastMessageId = builder.lastMessageId;
        this.lastMessageAt = builder.lastMessageAt;
        this.unreadCount = builder.unreadCount;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    // Getters
    public Long getId() { return id; }
    public ConversationType getType() { return type; }
    public String getSenderId() { return senderId; }
    public String getReceiverId() { return receiverId; }
    public Long getGroupId() { return groupId; }
    public Long getLastMessageId() { return lastMessageId; }
    public LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public int getUnreadCount() { return unreadCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // Business logic
    public boolean isPersonal() {
        return type == ConversationType.PERSONAL;
    }

    public boolean isGroup() {
        return type == ConversationType.GROUP;
    }

    public void incrementUnreadCount() {
        // Domain logic - có thể override trong service
        if (unreadCount >= Integer.MAX_VALUE - 1) {
            throw new IllegalStateException("Unread count overflow");
        }
    }

    public void resetUnreadCount() {
        // This would be handled by a new instance in immutable design
    }

    public void validate() {
        if (type == null) {
            throw new IllegalStateException("Conversation type is required");
        }
        if (type == ConversationType.PERSONAL && receiverId == null) {
            throw new IllegalStateException("Personal conversation requires receiver ID");
        }
        if (type == ConversationType.GROUP && groupId == null) {
            throw new IllegalStateException("Group conversation requires group ID");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Conversation that = (Conversation) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Conversation{" +
                "id=" + id +
                ", type=" + type +
                ", senderId=" + senderId +
                ", receiverId=" + receiverId +
                ", groupId=" + groupId +
                ", unreadCount=" + unreadCount +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private ConversationType type;
        private String senderId;
        private String receiverId;
        private Long groupId;
        private Long lastMessageId;
        private LocalDateTime lastMessageAt;
        private int unreadCount = 0;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder type(ConversationType type) {
            this.type = type;
            return this;
        }

        public Builder senderId(String senderId) {
            this.senderId = senderId;
            return this;
        }

        public Builder receiverId(String receiverId) {
            this.receiverId = receiverId;
            return this;
        }

        public Builder groupId(Long groupId) {
            this.groupId = groupId;
            return this;
        }

        public Builder lastMessageId(Long lastMessageId) {
            this.lastMessageId = lastMessageId;
            return this;
        }

        public Builder lastMessageAt(LocalDateTime lastMessageAt) {
            this.lastMessageAt = lastMessageAt;
            return this;
        }

        public Builder unreadCount(int unreadCount) {
            this.unreadCount = unreadCount;
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

        public Conversation build() {
            Conversation conversation = new Conversation(this);
            conversation.validate();
            return conversation;
        }
    }
}