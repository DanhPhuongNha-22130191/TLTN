package secretchat.chatservice.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import secretchat.chatservice.application.port.out.MessageReactionRepositoryPort;
import secretchat.chatservice.domain.model.MessageReaction;
import secretchat.chatservice.infrastructure.persistence.entity.MessageReactionEntity;
import secretchat.chatservice.infrastructure.persistence.repository.MessageReactionRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MessageReactionRepositoryAdapter implements MessageReactionRepositoryPort {
    private final MessageReactionRepository repository;

    @Override
    public List<MessageReaction> findByMessageId(Long messageId) {
        return repository.findByMessageIdOrderByIdAsc(messageId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<MessageReaction> findByMessageIdAndUserId(Long messageId, String userId) {
        return repository.findByMessageIdAndUserId(messageId, userId).map(this::toDomain);
    }

    @Override
    public MessageReaction save(MessageReaction reaction) {
        MessageReactionEntity entity = repository
                .findByMessageIdAndUserId(reaction.messageId(), reaction.userId())
                .orElseGet(MessageReactionEntity::new);
        entity.setMessageId(reaction.messageId());
        entity.setUserId(reaction.userId());
        entity.setEmoji(reaction.emoji());
        return toDomain(repository.save(entity));
    }

    @Override
    public void deleteByMessageIdAndUserId(Long messageId, String userId) {
        repository.deleteByMessageIdAndUserId(messageId, userId);
    }

    private MessageReaction toDomain(MessageReactionEntity entity) {
        return new MessageReaction(entity.getMessageId(), entity.getUserId(), entity.getEmoji());
    }
}
