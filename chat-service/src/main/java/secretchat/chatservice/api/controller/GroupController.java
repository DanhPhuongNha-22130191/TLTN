package secretchat.chatservice.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import secretchat.chatservice.api.mapper.GroupApiMapper;
import secretchat.chatservice.api.request.AddGroupMemberRequest;
import secretchat.chatservice.api.request.CreateGroupRequest;
import secretchat.chatservice.api.request.UpdateGroupRequest;
import secretchat.chatservice.api.request.UpdateMemberNicknameRequest;
import secretchat.chatservice.api.request.UpdateMemberRoleRequest;
import secretchat.chatservice.api.response.GroupMemberResponse;
import secretchat.chatservice.api.response.GroupResponse;
import secretchat.chatservice.application.port.in.GroupUseCase;
import secretchat.chatservice.domain.model.Group;
import secretchat.chatservice.domain.model.GroupMember;
import secretchat.chatservice.domain.enums.Role;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupUseCase groupUseCase;

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        Group group = groupUseCase.createGroup(GroupApiMapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(GroupApiMapper.toResponse(group));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GroupResponse> updateGroup(
            @PathVariable Long id,
            @Valid @RequestBody UpdateGroupRequest request) {
        Group group = groupUseCase.updateGroup(id, GroupApiMapper.toCommand(request));
        return ResponseEntity.ok(GroupApiMapper.toResponse(group));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupResponse> getGroupDetails(@PathVariable Long id) {
        Group group = groupUseCase.getGroupDetails(id);
        return ResponseEntity.ok(GroupApiMapper.toResponse(group));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(
            @PathVariable Long id,
            @RequestParam String userId) {
        groupUseCase.deleteGroup(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/members")
    public ResponseEntity<GroupResponse> addMember(
            @PathVariable Long groupId,
            @Valid @RequestBody AddGroupMemberRequest request) {
        Role role = request.getRole() != null ? Role.valueOf(request.getRole()) : null;
        Group group = groupUseCase.addMember(groupId, request.getUserId(), request.getInvitedBy(), role);
        return ResponseEntity.ok(GroupApiMapper.toResponse(group));
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long groupId,
            @PathVariable String userId) {
        groupUseCase.removeMember(groupId, userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{groupId}/members/{userId}/nickname")
    public ResponseEntity<GroupMemberResponse> updateMemberNickname(
            @PathVariable Long groupId,
            @PathVariable String userId,
            @Valid @RequestBody UpdateMemberNicknameRequest request) {
        GroupMember member = groupUseCase.updateMemberNickname(groupId, userId, request.getNickname());
        return ResponseEntity.ok(GroupApiMapper.toResponse(member));
    }

    @PutMapping("/{groupId}/members/{userId}/role")
    public ResponseEntity<GroupMemberResponse> updateMemberRole(
            @PathVariable Long groupId,
            @PathVariable String userId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        Role role = Role.valueOf(request.getRole());
        GroupMember member = groupUseCase.updateMemberRole(groupId, userId, role);
        return ResponseEntity.ok(GroupApiMapper.toResponse(member));
    }

    @PutMapping("/{groupId}/owner")
    public ResponseEntity<GroupResponse> transferOwnership(
            @PathVariable Long groupId,
            @RequestParam String currentOwnerId,
            @RequestParam String newOwnerId) {
        return ResponseEntity.ok(GroupApiMapper.toResponse(
                groupUseCase.transferOwnership(groupId, currentOwnerId, newOwnerId)));
    }

    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<GroupMemberResponse>> getGroupMembers(@PathVariable Long groupId) {
        List<GroupMember> members = groupUseCase.getGroupMembers(groupId);
        List<GroupMemberResponse> responses = members.stream()
                .map(GroupApiMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }
}
