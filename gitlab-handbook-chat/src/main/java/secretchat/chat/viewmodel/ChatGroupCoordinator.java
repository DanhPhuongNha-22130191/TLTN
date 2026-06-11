package secretchat.chat.viewmodel;

import javafx.application.Platform;
import secretchat.chat.service.ChatService;
import secretchat.dto.request.AddGroupMemberRequest;
import secretchat.dto.request.CreateGroupRequest;
import secretchat.dto.request.UpdateMemberRoleRequest;
import secretchat.dto.response.ConversationResponse;
import secretchat.dto.response.GroupMemberResponse;
import secretchat.dto.response.GroupResponse;
import secretchat.dto.response.UserResponse;
import secretchat.util.IdUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Coordinates group membership and ownership operations for the chat screen.
 */
final class ChatGroupCoordinator {
    interface Host {
        String token();
        String currentUserId();
        String currentChatName();
        ConversationResponse activeConversation();
        GroupResponse groupByName(String name);
        UserResponse userByName(String name);
        Set<Map.Entry<String, UserResponse>> users();
        String userDisplayName(String userId);
        boolean isGroupCreator(String groupName);
        void groupCreated(GroupResponse group, ConversationResponse conversation);
        void groupUpdated(GroupResponse group);
        void groupLeft(String groupName);
        void groupDeleted(GroupResponse group);
        void addMemberName(String name);
        void removeMemberName(String name);
        void reloadGroupInfo(GroupResponse group);
        void showError(String message);
        void showNotification(String message);
    }

    private final ChatService chatService;
    private final Host host;

    ChatGroupCoordinator(ChatService chatService, Host host) {
        this.chatService = chatService;
        this.host = host;
    }

    void createGroup(String groupName, String groupDesc) {
        try {
            CreateGroupRequest request = new CreateGroupRequest();
            request.setName(groupName);
            request.setDescription(groupDesc);
            request.setCreatorId(host.currentUserId());
            request.setMemberIds(new ArrayList<>(List.of(host.currentUserId())));

            GroupResponse group = chatService.createGroup(request, host.token());
            ConversationResponse conversation = chatService.createGroupConversation(
                    IdUtils.parseLongId(group.getId()), host.token());
            Platform.runLater(() -> host.groupCreated(group, conversation));
            host.showNotification("Đã tạo nhóm " + groupName);
        } catch (Exception error) {
            host.showError("Không thể tạo nhóm: " + error.getMessage());
        }
    }

    void addMember(String selectedUserName) {
        UserResponse user = host.userByName(selectedUserName);
        ConversationResponse conversation = host.activeConversation();
        if (user == null || conversation == null) return;

        try {
            AddGroupMemberRequest request = new AddGroupMemberRequest();
            request.setUserId(user.getId());
            request.setInvitedBy(host.currentUserId());
            request.setRole("MEMBER");
            chatService.addGroupMember(
                    IdUtils.parseLongId(conversation.getGroupId()), request, host.token());
            Platform.runLater(() -> host.addMemberName(selectedUserName));
            host.showNotification("Đã thêm " + selectedUserName + " vào nhóm.");
        } catch (Exception error) {
            host.showError("Không thể thêm thành viên: " + error.getMessage());
        }
    }

    void leaveGroup() {
        ConversationResponse conversation = host.activeConversation();
        if (conversation == null || conversation.getGroupId() == null) return;

        try {
            chatService.removeGroupMember(
                    IdUtils.parseLongId(conversation.getGroupId()),
                    host.currentUserId(),
                    host.token());
            String groupName = host.currentChatName();
            Platform.runLater(() -> host.groupLeft(groupName));
            host.showNotification("Bạn đã rời khỏi nhóm.");
        } catch (Exception error) {
            host.showError("Không thể rời nhóm: " + error.getMessage());
        }
    }

    void kickMember(String memberName) {
        GroupResponse group = host.groupByName(host.currentChatName());
        if (group == null) return;

        try {
            String targetUserId = findMemberId(group, memberName, true);
            if (targetUserId == null) {
                host.showError("Không tìm thấy thông tin thành viên.");
                return;
            }
            chatService.removeGroupMember(
                    IdUtils.parseLongId(group.getId()), targetUserId, host.token());
            Platform.runLater(() -> host.removeMemberName(memberName));
            host.showNotification("Đã kick " + memberName + " khỏi nhóm.");
        } catch (Exception error) {
            host.showError("Không thể kick thành viên: " + error.getMessage());
        }
    }

    void updateRole(String memberName, String newRole) {
        GroupResponse group = host.groupByName(host.currentChatName());
        if (group == null) return;

        try {
            String targetUserId = findMemberId(group, memberName, false);
            if (targetUserId == null) {
                host.showError("Không tìm thấy thông tin thành viên.");
                return;
            }
            UpdateMemberRoleRequest request = new UpdateMemberRoleRequest();
            request.setRole(newRole);
            chatService.updateMemberRole(
                    IdUtils.parseLongId(group.getId()), targetUserId, request, host.token());
            host.reloadGroupInfo(group);
            String roleName = "ADMIN".equals(newRole) ? "Phó nhóm" : "Thành viên";
            host.showNotification(
                    "Đã cập nhật quyền của " + memberName + " thành " + roleName + ".");
        } catch (Exception error) {
            host.showError("Không thể cập nhật quyền: " + error.getMessage());
        }
    }

    List<ChatViewModel.GroupMemberView> getCurrentMembers() {
        GroupResponse group = host.groupByName(host.currentChatName());
        if (group == null) return List.of();
        try {
            GroupMemberResponse[] members = chatService.getGroupMembersList(
                    IdUtils.parseLongId(group.getId()), host.token());
            if (members == null) return List.of();
            return Arrays.stream(members)
                    .map(member -> new ChatViewModel.GroupMemberView(
                            member.getUserId(),
                            member.getUserId().equals(host.currentUserId())
                                    ? "Bạn" : host.userDisplayName(member.getUserId()),
                            member.getRole()))
                    .toList();
        } catch (Exception error) {
            host.showError("Không thể tải thành viên nhóm: " + error.getMessage());
            return List.of();
        }
    }

    List<String> getAvailableMemberNames() {
        Set<String> existingIds = getCurrentMembers().stream()
                .map(ChatViewModel.GroupMemberView::userId)
                .collect(Collectors.toSet());
        return host.users().stream()
                .filter(entry -> !existingIds.contains(entry.getValue().getId()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    void removeMemberById(String userId) {
        GroupResponse group = host.groupByName(host.currentChatName());
        if (group == null || !host.isGroupCreator(host.currentChatName())
                || userId.equals(host.currentUserId())) {
            return;
        }
        try {
            chatService.removeGroupMember(
                    IdUtils.parseLongId(group.getId()), userId, host.token());
            host.reloadGroupInfo(group);
            host.showNotification("Đã xóa thành viên khỏi nhóm.");
        } catch (Exception error) {
            host.showError("Không thể xóa thành viên: " + error.getMessage());
        }
    }

    void transferOwnership(String newOwnerId) {
        GroupResponse group = host.groupByName(host.currentChatName());
        if (group == null || !host.isGroupCreator(host.currentChatName())
                || newOwnerId.equals(host.currentUserId())) {
            return;
        }
        try {
            GroupResponse updated = chatService.transferGroupOwnership(
                    IdUtils.parseLongId(group.getId()),
                    host.currentUserId(),
                    newOwnerId,
                    host.token());
            host.groupUpdated(updated);
            host.reloadGroupInfo(updated);
            host.showNotification("Đã chuyển quyền chủ nhóm.");
        } catch (Exception error) {
            host.showError("Không thể chuyển quyền chủ nhóm: " + error.getMessage());
        }
    }

    void deleteCurrentGroup() {
        GroupResponse group = host.groupByName(host.currentChatName());
        if (group == null || !host.currentUserId().equals(group.getCreatorId())) {
            host.showError("Chỉ chủ nhóm mới có thể xóa nhóm.");
            return;
        }
        try {
            chatService.deleteGroup(
                    IdUtils.parseLongId(group.getId()), host.currentUserId(), host.token());
            Platform.runLater(() -> host.groupDeleted(group));
            host.showNotification("Đã xóa nhóm " + group.getName() + ".");
        } catch (Exception error) {
            host.showError("Không thể xóa nhóm: " + error.getMessage());
        }
    }

    private String findMemberId(
            GroupResponse group, String memberName, boolean includeRoleSuffix) throws Exception {
        GroupMemberResponse[] members = chatService.getGroupMembersList(
                IdUtils.parseLongId(group.getId()), host.token());
        if (members == null) return null;

        for (GroupMemberResponse member : members) {
            String displayName = host.userDisplayName(member.getUserId());
            if (includeRoleSuffix) {
                if ("OWNER".equals(member.getRole())) displayName += " (Chủ nhóm)";
                else if ("ADMIN".equals(member.getRole())) displayName += " (Phó nhóm)";
            }
            if (displayName.equals(memberName)
                    || ("Bạn".equals(memberName)
                    && member.getUserId().equals(host.currentUserId()))) {
                return member.getUserId();
            }
        }
        return null;
    }
}
