package secretchat.chatservice.api.mapper;

import secretchat.chatservice.api.request.CreateGroupRequest;
import secretchat.chatservice.api.request.UpdateGroupRequest;
import secretchat.chatservice.api.response.GroupMemberResponse;
import secretchat.chatservice.api.response.GroupResponse;
import secretchat.chatservice.application.usecase.command.CreateGroupCommand;
import secretchat.chatservice.application.usecase.command.UpdateGroupCommand;
import secretchat.chatservice.domain.model.Group;
import secretchat.chatservice.domain.model.GroupMember;

import java.util.stream.Collectors;

public final class GroupApiMapper {

    private GroupApiMapper() {}

    public static CreateGroupCommand toCommand(CreateGroupRequest request) {
        if (request == null) {
            return null;
        }
        return CreateGroupCommand.builder()
                .name(request.getName())
                .description(request.getDescription())
                .creatorId(request.getCreatorId())
                .avatarUrl(request.getAvatarUrl())
                .memberIds(request.getMemberIds())
                .build();
    }

    public static UpdateGroupCommand toCommand(UpdateGroupRequest request) {
        if (request == null) {
            return null;
        }
        return UpdateGroupCommand.builder()
                .name(request.getName())
                .description(request.getDescription())
                .avatarUrl(request.getAvatarUrl())
                .build();
    }

    public static GroupMemberResponse toResponse(GroupMember domain) {
        if (domain == null) {
            return null;
        }
        return GroupMemberResponse.builder()
                .groupId(domain.getGroupId())
                .userId(domain.getUserId())
                .role(domain.getRole().name())
                .nickname(domain.getNickname())
                .invitedBy(domain.getInvitedBy())
                .joinedAt(domain.getJoinedAt())
                .build();
    }

    public static GroupResponse toResponse(Group domain) {
        if (domain == null) {
            return null;
        }
        return GroupResponse.builder()
                .id(domain.getId())
                .name(domain.getName())
                .description(domain.getDescription())
                .creatorId(domain.getCreatorId())
                .avatarUrl(domain.getAvatarUrl())
                .isActive(domain.isActive())
                .members(domain.getMembers() == null ? null : domain.getMembers().stream()
                        .map(GroupApiMapper::toResponse)
                        .collect(Collectors.toList()))
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}
