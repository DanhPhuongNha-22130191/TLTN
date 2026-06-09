package secretchat.chatservice.domain.model;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure domain entity
 */
public class Group {

    private final Long id;
    private final String name;
    private final String description;
    private final String creatorId;
    private final String avatarUrl;
    private final boolean isActive;
    private final List<GroupMember> members;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private Group(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.creatorId = builder.creatorId;
        this.avatarUrl = builder.avatarUrl;
        this.isActive = builder.isActive;
        this.members = builder.members != null ? new ArrayList<>(builder.members) : new ArrayList<>();
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    // Getters
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCreatorId() { return creatorId; }
    public String getAvatarUrl() { return avatarUrl; }
    public boolean isActive() { return isActive; }
    public List<GroupMember> getMembers() { return new ArrayList<>(members); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // Business logic
    public int getMemberCount() {
        return members.size();
    }

    public boolean hasMember(String userId) {
        return members.stream()
                .anyMatch(m -> Objects.equals(m.getUserId(), userId));
    }

    public GroupMember getMember(String userId) {
        return members.stream()
                .filter(m -> Objects.equals(m.getUserId(), userId))
                .findFirst()
                .orElse(null);
    }

    public boolean isAdmin(String userId) {
        GroupMember member = getMember(userId);
        return member != null && (member.isOwner() || member.isAdmin());
    }

    public boolean isOwner(String userId) {
        GroupMember member = getMember(userId);
        return member != null && member.isOwner();
    }

    public boolean canAddMember(String userId) {
        return isActive && isAdmin(userId);
    }

    public boolean canRemoveMember(String userId) {
        return isActive && isAdmin(userId);
    }

    public boolean canDeleteGroup(String userId) {
        return isActive && isOwner(userId);
    }

    public boolean canManageSettings(String userId) {
        return isActive && isAdmin(userId);
    }

    public void validate() {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalStateException("Group name is required");
        }
        if (name.length() > 255) {
            throw new IllegalStateException("Group name too long (max 255 characters)");
        }
        if (creatorId == null) {
            throw new IllegalStateException("Creator ID is required");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Group group = (Group) o;
        return Objects.equals(id, group.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Group{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", creatorId=" + creatorId +
                ", memberCount=" + getMemberCount() +
                ", isActive=" + isActive +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String name;
        private String description;
        private String creatorId;
        private String avatarUrl;
        private boolean isActive = true;
        private List<GroupMember> members = new ArrayList<>();
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder creatorId(String creatorId) {
            this.creatorId = creatorId;
            return this;
        }

        public Builder avatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
            return this;
        }

        public Builder isActive(boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public Builder members(List<GroupMember> members) {
            this.members = members != null ? new ArrayList<>(members) : new ArrayList<>();
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

        public Group build() {
            Group group = new Group(this);
            group.validate();
            return group;
        }
    }
}