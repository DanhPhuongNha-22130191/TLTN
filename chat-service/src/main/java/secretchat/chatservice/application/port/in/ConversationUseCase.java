package secretchat.chatservice.application.port.in;

import secretchat.chatservice.domain.model.Conversation;

import java.util.List;

public interface ConversationUseCase {
    Conversation createPersonalConversation(String senderId, String receiverId);
    Conversation createGroupConversation(Long groupId);
    Conversation getConversation(Long id);
    List<Conversation> getUserConversations(String userId);
    void deleteConversation(Long id);
    Conversation markAsRead(Long conversationId);
}
