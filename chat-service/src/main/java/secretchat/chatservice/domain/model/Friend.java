package secretchat.chatservice.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class Friend {

    private final String id;
    private final String userId;
    private final String friendId;
    private final FriendStatus status;
    private final LocalDateTime createdAt;

    private Friend(Builder builder) {
        this.id = builder.id;
        this.userId = builder.userId;
        this.friendId = builder.friendId;
        this.status = builder.status;
        this.createdAt = builder.createdAt;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getFriendId() {
        return friendId;
    }

    public FriendStatus getStatus() {
        return status == null ? FriendStatus.ACCEPTED : status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void validate() {
        if (userId == null || friendId == null) {
            throw new IllegalStateException("Both userId and friendId are required");
        }
        if (userId.equals(friendId)) {
            throw new IllegalStateException("Cannot add yourself as a friend");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Friend friend = (Friend) o;
        return Objects.equals(id, friend.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Friend{" +
                "id=" + id +
                ", userId=" + userId +
                ", friendId=" + friendId +
                ", createdAt=" + createdAt +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String userId;
        private String friendId;
        private FriendStatus status;
        private LocalDateTime createdAt;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder friendId(String friendId) {
            this.friendId = friendId;
            return this;
        }

        public Builder status(FriendStatus status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Friend build() {
            Friend friend = new Friend(this);
            friend.validate();
            return friend;
        }
    }
}
