package secretchat.chatservice.infrastructure.persistence.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import secretchat.chatservice.domain.model.Group;
import secretchat.chatservice.infrastructure.persistence.entity.GroupEntity;
import secretchat.chatservice.infrastructure.persistence.entity.GroupMemberEntity;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GroupMapper {

    private final GroupMemberMapper groupMemberMapper;

    public Group toDomain(GroupEntity entity) {
        if (entity == null) {
            return null;
        }
        return Group.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .creatorId(entity.getCreatorId())
                .avatarUrl(entity.getAvatarUrl())
                .isActive(entity.isActive())
                .members(entity.getMembers() == null ? null : entity.getMembers().stream()
                        .map(groupMemberMapper::toDomain)
                        .collect(Collectors.toList()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public GroupEntity toEntity(Group domain) {
        if (domain == null) {
            return null;
        }
        GroupEntity groupEntity = GroupEntity.builder()
                .id(domain.getId())
                .name(domain.getName())
                .description(domain.getDescription())
                .creatorId(domain.getCreatorId())
                .avatarUrl(domain.getAvatarUrl())
                .isActive(domain.isActive())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();

        if (domain.getMembers() != null && !domain.getMembers().isEmpty()) {
            List<GroupMemberEntity> memberEntities = domain.getMembers().stream()
                    .map(member -> {
                        GroupMemberEntity memberEntity = groupMemberMapper.toEntity(member);
                        if (memberEntity != null) {
                            memberEntity.setGroup(groupEntity);
                        }
                        return memberEntity;
                    })
                    .collect(Collectors.toList());
            groupEntity.setMembers(memberEntities);
        }
        return groupEntity;
    }
}
