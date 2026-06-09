package secretchat.chatservice.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import secretchat.chatservice.domain.model.GroupMember;
import secretchat.chatservice.domain.enums.Role;
import secretchat.chatservice.infrastructure.persistence.entity.GroupMemberEntity;

@Component
public class GroupMemberMapper {

    public GroupMemberEntity toEntity(GroupMember domain) {
        if (domain == null) {
            return null;
        }
        return GroupMemberEntity.builder()
                .groupId(domain.getGroupId())
                .userId(domain.getUserId())
                .role(domain.getRole().name())
                .nickname(domain.getNickname())
                .invitedBy(domain.getInvitedBy())
                .joinedAt(domain.getJoinedAt())
                .build();
    }

    public GroupMember toDomain(GroupMemberEntity entity) {
        if (entity == null) {
            return null;
        }
        return GroupMember.builder()
                .groupId(entity.getGroupId())
                .userId(entity.getUserId())
                .role(Role.valueOf(entity.getRole()))
                .nickname(entity.getNickname())
                .invitedBy(entity.getInvitedBy())
                .joinedAt(entity.getJoinedAt())
                .build();
    }
}
