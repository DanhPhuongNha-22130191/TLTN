package secretchat.chatservice.application.port.out;

import secretchat.chatservice.domain.model.GroupMember;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepositoryPort {
    GroupMember save(GroupMember groupMember);
    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, String userId);
    List<GroupMember> findByGroupId(Long groupId);
    List<GroupMember> findByUserId(String userId);
    void delete(GroupMember groupMember);
    boolean existsByGroupIdAndUserId(Long groupId, String userId);
}
