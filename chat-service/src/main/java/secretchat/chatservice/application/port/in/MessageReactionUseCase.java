package secretchat.chatservice.application.port.in;

import secretchat.chatservice.domain.model.MessageReaction;

import java.util.List;

public interface MessageReactionUseCase {
    List<MessageReaction> getReactions(Long messageId);
    void setReaction(Long messageId, String userId, String emoji);
}
