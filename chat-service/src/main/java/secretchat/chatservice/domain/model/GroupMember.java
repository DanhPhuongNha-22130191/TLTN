package secretchat.chatservice.domain.model;


import secretchat.chatservice.domain.enums.Role;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Pure domain entity
 */
public class GroupMember {

    private final Long groupId;
    private final String userId;
    private final Role role;
    private final String nickname;
    private final String invitedBy;
    private final LocalDateTime joinedAt;

    private GroupMember(Builder builder) {
        this.groupId = builder.groupId;
        this.userId = builder.userId;
        this.role = builder.role;
        this.nickname = builder.nickname;
        this.invitedBy = builder.invitedBy;
        this.joinedAt = builder.joinedAt;
    }

    // Getters
    public Long getGroupId() { return groupId; }
    public String getUserId() { return userId; }
    public Role getRole() { return role; }
    public String getNickname() { return nickname; }
    public String getInvitedBy() { return invitedBy; }
    public LocalDateTime getJoinedAt() { return joinedAt; }

    // Business logic
    public boolean isOwner() {
        return role == Role.OWNER;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isMember() {
        return role == Role.MEMBER;
    }

    public boolean isOwnerOrAdmin() {
        return role == Role.OWNER || role == Role.ADMIN;
    }

    public boolean canManageGroup() {
        return isOwner() || isAdmin();
    }

    public boolean canAddMember() {
        return isOwner() || isAdmin();
    }

    public boolean canRemoveMember() {
        return isOwner() || isAdmin();
    }

    public boolean canDeleteGroup() {
        return isOwner();
    }

    public boolean canTransferOwnership() {
        return isOwner();
    }

    public boolean canPromoteToAdmin() {
        return isOwner();
    }

    public boolean canDemoteAdmin() {
        return isOwner();
    }

    public boolean canDeleteMessages() {
        return isOwner() || isAdmin();
    }

    public void validate() {
        if (groupId == null) {
            throw new IllegalStateException("Group ID is required");
        }
        if (userId == null) {
            throw new IllegalStateException("User ID is required");
        }
        if (role == null) {
            throw new IllegalStateException("Role is required");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GroupMember that = (GroupMember) o;
        return Objects.equals(groupId, that.groupId) &&
                Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, userId);
    }

    @Override
    public String toString() {
        return "GroupMember{" +
                "groupId=" + groupId +
                ", userId=" + userId +
                ", role=" + role +
                ", nickname='" + nickname + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long groupId;
        private String userId;
        private Role role = Role.MEMBER;
        private String nickname;
        private String invitedBy;
        private LocalDateTime joinedAt;

        public Builder groupId(Long groupId) {
            this.groupId = groupId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder role(Role role) {
            this.role = role;
            return this;
        }

        public Builder nickname(String nickname) {
            this.nickname = nickname;
            return this;
        }

        public Builder invitedBy(String invitedBy) {
            this.invitedBy = invitedBy;
            return this;
        }

        public Builder joinedAt(LocalDateTime joinedAt) {
            this.joinedAt = joinedAt;
            return this;
        }

        public GroupMember build() {
            GroupMember member = new GroupMember(this);
            member.validate();
            return member;
        }
    }


}
