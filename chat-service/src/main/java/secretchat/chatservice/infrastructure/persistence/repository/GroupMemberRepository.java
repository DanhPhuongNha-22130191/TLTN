package secretchat.chatservice.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import secretchat.chatservice.infrastructure.persistence.entity.GroupMemberEntity;
import secretchat.chatservice.infrastructure.persistence.entity.GroupMemberId;

import java.util.List;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMemberEntity, GroupMemberId> {
    List<GroupMemberEntity> findByGroupId(Long groupId);
    java.util.Optional<GroupMemberEntity> findByGroupIdAndUserId(Long groupId, String userId);
    List<GroupMemberEntity> findByUserId(String userId);
    boolean existsByGroupIdAndUserId(Long groupId, String userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM GroupMemberEntity gm WHERE gm.groupId = :groupId AND gm.userId = :userId")
    void deleteByGroupIdAndUserId(@org.springframework.data.repository.query.Param("groupId") Long groupId, @org.springframework.data.repository.query.Param("userId") String userId);
}
