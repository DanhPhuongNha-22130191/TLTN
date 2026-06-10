package secretchat.chatservice.application.port.out;

import secretchat.chatservice.domain.model.MessageReaction;

import java.util.List;
import java.util.Optional;

public interface MessageReactionRepositoryPort {
    List<MessageReaction> findByMessageId(Long messageId);
    Optional<MessageReaction> findByMessageIdAndUserId(Long messageId, String userId);
    MessageReaction save(MessageReaction reaction);
    void deleteByMessageIdAndUserId(Long messageId, String userId);
}
