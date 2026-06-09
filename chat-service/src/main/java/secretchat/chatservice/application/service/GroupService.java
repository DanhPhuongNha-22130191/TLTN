package secretchat.chatservice.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import secretchat.chatservice.application.port.in.GroupUseCase;
import secretchat.chatservice.application.port.out.GroupMemberRepositoryPort;
import secretchat.chatservice.application.port.out.GroupRepositoryPort;
import secretchat.chatservice.application.port.out.ConversationRepositoryPort;
import secretchat.chatservice.application.usecase.command.CreateGroupCommand;
import secretchat.chatservice.application.usecase.command.UpdateGroupCommand;
import secretchat.chatservice.domain.model.Group;
import secretchat.chatservice.domain.model.GroupMember;
import secretchat.chatservice.domain.enums.Role;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupService implements GroupUseCase {

    private final GroupRepositoryPort groupRepositoryPort;
    private final GroupMemberRepositoryPort groupMemberRepositoryPort;
    private final ConversationRepositoryPort conversationRepositoryPort;

    @Override
    @Transactional
    public Group createGroup(CreateGroupCommand command) {
        // Create group first without members to get the auto-generated ID
        Group group = Group.builder()
                .name(command.getName())
                .description(command.getDescription())
                .creatorId(command.getCreatorId())
                .avatarUrl(command.getAvatarUrl())
                .isActive(true)
                .members(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Save group to get the auto-generated ID
        Group savedGroup = groupRepositoryPort.save(group);

        // Now save the members with the correct group ID
        List<GroupMember> savedMembers = new ArrayList<>();

        // Add creator as OWNER
        GroupMember creatorMember = GroupMember.builder()
                .groupId(savedGroup.getId())
                .userId(command.getCreatorId())
                .role(Role.OWNER)
                .joinedAt(LocalDateTime.now())
                .build();
        savedMembers.add(groupMemberRepositoryPort.save(creatorMember));

        // Add other members if provided
        if (command.getMemberIds() != null) {
            for (String userId : command.getMemberIds()) {
                if (!userId.equals(command.getCreatorId())) {
                    GroupMember member = GroupMember.builder()
                            .groupId(savedGroup.getId())
                            .userId(userId)
                            .role(Role.MEMBER)
                            .invitedBy(command.getCreatorId())
                            .joinedAt(LocalDateTime.now())
                            .build();
                    savedMembers.add(groupMemberRepositoryPort.save(member));
                }
            }
        }

        return Group.builder()
                .id(savedGroup.getId())
                .name(savedGroup.getName())
                .description(savedGroup.getDescription())
                .creatorId(savedGroup.getCreatorId())
                .avatarUrl(savedGroup.getAvatarUrl())
                .isActive(savedGroup.isActive())
                .members(savedMembers)
                .createdAt(savedGroup.getCreatedAt())
                .updatedAt(savedGroup.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public Group updateGroup(Long groupId, UpdateGroupCommand command) {
        Group group = getGroupDetails(groupId);

        Group updatedGroup = Group.builder()
                .id(group.getId())
                .name(command.getName() != null ? command.getName() : group.getName())
                .description(command.getDescription() != null ? command.getDescription() : group.getDescription())
                .creatorId(group.getCreatorId())
                .avatarUrl(command.getAvatarUrl() != null ? command.getAvatarUrl() : group.getAvatarUrl())
                .isActive(group.isActive())
                .members(group.getMembers())
                .createdAt(group.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();

        return groupRepositoryPort.save(updatedGroup);
    }

    @Override
    public Group getGroupDetails(Long groupId) {
        return groupRepositoryPort.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found with id: " + groupId));
    }

    @Override
    @Transactional
    public void deleteGroup(Long groupId, String userId) {
        Group group = getGroupDetails(groupId);
        if (userId == null || !userId.equals(group.getCreatorId())) {
            throw new secretchat.chatservice.application.exception.BusinessException(
                    "Only the group owner can delete this group");
        }
        conversationRepositoryPort.findByGroupId(groupId)
                .ifPresent(conversation -> conversationRepositoryPort.deleteById(conversation.getId()));
        groupRepositoryPort.deleteById(groupId);
    }

    @Override
    @Transactional
    public Group addMember(Long groupId, String userId, String invitedBy, Role role) {
        Group group = getGroupDetails(groupId);

        if (group.hasMember(userId)) {
            throw new IllegalArgumentException("User is already a member of the group");
        }

        GroupMember newMember = GroupMember.builder()
                .groupId(groupId)
                .userId(userId)
                .role(role != null ? role : Role.MEMBER)
                .invitedBy(invitedBy)
                .joinedAt(LocalDateTime.now())
                .build();

        groupMemberRepositoryPort.save(newMember);

        return getGroupDetails(groupId);
    }

    @Override
    @Transactional
    public void removeMember(Long groupId, String userId) {
        Group group = getGroupDetails(groupId);
        GroupMember member = group.getMember(userId);

        if (member == null) {
            throw new IllegalArgumentException("User is not a member of the group");
        }

        groupMemberRepositoryPort.delete(member);
    }

    @Override
    @Transactional
    public GroupMember updateMemberNickname(Long groupId, String userId, String nickname) {
        GroupMember member = groupMemberRepositoryPort.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Group member not found"));

        GroupMember updatedMember = GroupMember.builder()
                .groupId(member.getGroupId())
                .userId(member.getUserId())
                .role(member.getRole())
                .nickname(nickname)
                .invitedBy(member.getInvitedBy())
                .joinedAt(member.getJoinedAt())
                .build();

        return groupMemberRepositoryPort.save(updatedMember);
    }

    @Override
    @Transactional
    public GroupMember updateMemberRole(Long groupId, String userId, Role role) {
        GroupMember member = groupMemberRepositoryPort.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Group member not found"));

        GroupMember updatedMember = GroupMember.builder()
                .groupId(member.getGroupId())
                .userId(member.getUserId())
                .role(role)
                .nickname(member.getNickname())
                .invitedBy(member.getInvitedBy())
                .joinedAt(member.getJoinedAt())
                .build();

        return groupMemberRepositoryPort.save(updatedMember);
    }

    @Override
    @Transactional
    public Group transferOwnership(Long groupId, String currentOwnerId, String newOwnerId) {
        Group group = getGroupDetails(groupId);
        if (!group.getCreatorId().equals(currentOwnerId)) {
            throw new IllegalArgumentException("Only the group owner can transfer ownership");
        }
        GroupMember currentOwner = groupMemberRepositoryPort.findByGroupIdAndUserId(groupId, currentOwnerId)
                .orElseThrow(() -> new IllegalArgumentException("Current owner is not a group member"));
        GroupMember newOwner = groupMemberRepositoryPort.findByGroupIdAndUserId(groupId, newOwnerId)
                .orElseThrow(() -> new IllegalArgumentException("New owner is not a group member"));

        groupMemberRepositoryPort.save(copyMemberWithRole(currentOwner, Role.MEMBER));
        groupMemberRepositoryPort.save(copyMemberWithRole(newOwner, Role.OWNER));
        List<GroupMember> updatedMembers = group.getMembers().stream()
                .map(member -> member.getUserId().equals(currentOwnerId)
                        ? copyMemberWithRole(member, Role.MEMBER)
                        : member.getUserId().equals(newOwnerId)
                                ? copyMemberWithRole(member, Role.OWNER)
                                : member)
                .toList();

        return groupRepositoryPort.save(Group.builder()
                .id(group.getId()).name(group.getName()).description(group.getDescription())
                .creatorId(newOwnerId).avatarUrl(group.getAvatarUrl()).isActive(group.isActive())
                .members(updatedMembers).createdAt(group.getCreatedAt()).updatedAt(LocalDateTime.now())
                .build());
    }

    private GroupMember copyMemberWithRole(GroupMember member, Role role) {
        return GroupMember.builder()
                .groupId(member.getGroupId()).userId(member.getUserId()).role(role)
                .nickname(member.getNickname()).invitedBy(member.getInvitedBy())
                .joinedAt(member.getJoinedAt()).build();
    }

    @Override
    public List<GroupMember> getGroupMembers(Long groupId) {
        return groupMemberRepositoryPort.findByGroupId(groupId);
    }
}
