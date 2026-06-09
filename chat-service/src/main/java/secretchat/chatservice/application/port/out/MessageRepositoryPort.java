package secretchat.chatservice.application.port.out;

import secretchat.chatservice.domain.model.Message;

import java.util.List;

public interface MessageRepositoryPort {
    Message save(Message message);
    List<Message> findByConversationId(Long conversationId);
    Message findById(Long id);
    List<Message> findPinnedByConversationId(Long conversationId);
    List<Message> searchByConversationId(Long conversationId, String query);
}
