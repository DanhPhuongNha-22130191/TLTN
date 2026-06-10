package secretchat.chatservice.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import secretchat.chatservice.infrastructure.persistence.entity.MessageReactionEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageReactionRepository extends JpaRepository<MessageReactionEntity, Long> {
    List<MessageReactionEntity> findByMessageIdOrderByIdAsc(Long messageId);
    Optional<MessageReactionEntity> findByMessageIdAndUserId(Long messageId, String userId);
    void deleteByMessageIdAndUserId(Long messageId, String userId);
}
