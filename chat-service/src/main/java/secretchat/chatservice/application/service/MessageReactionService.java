package secretchat.chatservice.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import secretchat.chatservice.application.exception.BusinessException;
import secretchat.chatservice.application.port.in.MessageReactionUseCase;
import secretchat.chatservice.application.port.out.MessageReactionRepositoryPort;
import secretchat.chatservice.application.port.out.MessageRepositoryPort;
import secretchat.chatservice.domain.model.MessageReaction;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MessageReactionService implements MessageReactionUseCase {
    private static final Set<String> ALLOWED_EMOJIS =
            Set.of("👍", "❤️", "😂", "😮", "😢", "😡");

    private final MessageReactionRepositoryPort reactionRepository;
    private final MessageRepositoryPort messageRepository;

    @Override
    public List<MessageReaction> getReactions(Long messageId) {
        return reactionRepository.findByMessageId(messageId);
    }

    @Override
    @Transactional
    public void setReaction(Long messageId, String userId, String emoji) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException("User ID is required");
        }
        messageRepository.findById(messageId);
        if (emoji == null || emoji.isBlank()) {
            reactionRepository.deleteByMessageIdAndUserId(messageId, userId);
            return;
        }
        if (!ALLOWED_EMOJIS.contains(emoji)) {
            throw new BusinessException("Unsupported reaction");
        }

        MessageReaction reaction = reactionRepository
                .findByMessageIdAndUserId(messageId, userId)
                .map(current -> new MessageReaction(messageId, userId, emoji))
                .orElseGet(() -> new MessageReaction(messageId, userId, emoji));
        reactionRepository.save(reaction);
    }
}
