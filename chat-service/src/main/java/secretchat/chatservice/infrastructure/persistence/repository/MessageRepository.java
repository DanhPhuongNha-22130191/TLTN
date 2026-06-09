package secretchat.chatservice.infrastructure.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import secretchat.chatservice.infrastructure.persistence.entity.MessageEntity;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, Long> {

    Page<MessageEntity> findByConversationIdAndIsDeletedFalse(
            Long conversationId,
            Pageable pageable
    );

    List<MessageEntity> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    boolean existsByIdAndIsDeletedFalse(Long id);

    List<MessageEntity> findByConversationIdAndPinnedTrueAndIsDeletedFalseOrderByCreatedAtAsc(Long conversationId);

    List<MessageEntity> findByConversationIdAndContentContainingIgnoreCaseAndIsDeletedFalseOrderByCreatedAtAsc(
            Long conversationId, String content);
}
