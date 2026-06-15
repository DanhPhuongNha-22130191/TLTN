package secretchat.chatservice.application.port.in;

import secretchat.chatservice.application.usecase.command.CreateGroupCommand;
import secretchat.chatservice.application.usecase.command.UpdateGroupCommand;
import secretchat.chatservice.domain.model.Group;
import secretchat.chatservice.domain.model.GroupMember;
import secretchat.chatservice.domain.enums.Role;

import java.util.List;

public interface GroupUseCase {
    Group createGroup(CreateGroupCommand command);
    Group updateGroup(Long groupId, UpdateGroupCommand command);
    Group getGroupDetails(Long groupId);
    List<Group> getAllGroups();
    void deleteGroup(Long groupId, String userId);
    
    // Group Member management
    Group addMember(Long groupId, String userId, String invitedBy, Role role);
    void removeMember(Long groupId, String userId);
    GroupMember updateMemberNickname(Long groupId, String userId, String nickname);
    GroupMember updateMemberRole(Long groupId, String userId, Role role);
    Group transferOwnership(Long groupId, String currentOwnerId, String newOwnerId);
    List<GroupMember> getGroupMembers(Long groupId);
}
