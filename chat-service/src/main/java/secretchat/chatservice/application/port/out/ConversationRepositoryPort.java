package secretchat.chatservice.application.port.out;

import secretchat.chatservice.domain.model.Conversation;

import java.util.List;
import java.util.Optional;

public interface ConversationRepositoryPort {
    Conversation save(Conversation conversation);
    Optional<Conversation> findById(Long id);
    List<Conversation> findAll();
    void deleteById(Long id);
    boolean existsById(Long id);
    Optional<Conversation> findPersonalConversation(String user1, String user2);
    Optional<Conversation> findByGroupId(Long groupId);
    List<Conversation> findUserConversations(String userId);
}
