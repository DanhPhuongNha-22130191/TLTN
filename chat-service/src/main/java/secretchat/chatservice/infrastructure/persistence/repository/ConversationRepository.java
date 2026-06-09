package secretchat.chatservice.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import secretchat.chatservice.infrastructure.persistence.entity.ConversationEntity;

import java.util.List;

@Repository
public interface ConversationRepository extends JpaRepository<ConversationEntity, Long> {
    @Query("SELECT c FROM ConversationEntity c WHERE c.type = 'PERSONAL' AND " +
           "((c.senderId = :user1 AND c.receiverId = :user2) OR (c.senderId = :user2 AND c.receiverId = :user1))")
    java.util.Optional<ConversationEntity> findPersonalConversation(@Param("user1") String user1, @Param("user2") String user2);

    java.util.Optional<ConversationEntity> findByGroupId(Long groupId);

    @Query("SELECT DISTINCT c FROM ConversationEntity c LEFT JOIN MessageEntity m ON c.id = m.conversationId " +
           "WHERE (c.type = 'PERSONAL' AND (c.receiverId = :userId OR m.senderId = :userId)) " +
           "OR (c.type = 'GROUP' AND c.groupId IN (SELECT gm.groupId FROM GroupMemberEntity gm WHERE gm.userId = :userId))")
    List<ConversationEntity> findUserConversations(@Param("userId") String userId);
}
