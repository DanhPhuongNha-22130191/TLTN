package secretchat.chatservice.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class UserProfile {

    private final String id;
    private final String username;
    private final String externalSub;
    private final String email;
    private final String displayName;
    private final String avatarUrl;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private UserProfile(Builder builder) {
        this.id = builder.id;
        this.username = builder.username;
        this.externalSub = builder.externalSub;
        this.email = builder.email;
        this.displayName = builder.displayName;
        this.avatarUrl = builder.avatarUrl;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getExternalSub() {
        return externalSub;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void validate() {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalStateException("Username is required");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserProfile that = (UserProfile) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "UserProfile{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", externalSub='" + externalSub + '\'' +
                ", displayName='" + displayName + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String username;
        private String externalSub;
        private String email;
        private String displayName;
        private String avatarUrl;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder externalSub(String externalSub) {
            this.externalSub = externalSub;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder avatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
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

        public UserProfile build() {
            UserProfile profile = new UserProfile(this);
            profile.validate();
            return profile;
        }
    }
}
