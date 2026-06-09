package secretchat.chatservice.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import secretchat.chatservice.application.port.in.ConversationUseCase;
import secretchat.chatservice.application.port.out.ConversationRepositoryPort;
import secretchat.chatservice.domain.enums.ConversationType;
import secretchat.chatservice.domain.model.Conversation;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService implements ConversationUseCase {

    private final ConversationRepositoryPort conversationRepositoryPort;

    @Override
    public Conversation createPersonalConversation(String senderId, String receiverId) {
        return conversationRepositoryPort.findPersonalConversation(senderId, receiverId)
                .orElseGet(() -> {
                    Conversation conversation = Conversation.builder()
                            .type(ConversationType.PERSONAL)
                            .senderId(senderId)
                            .receiverId(receiverId)
                            .unreadCount(0)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return conversationRepositoryPort.save(conversation);
                });
    }

    @Override
    public Conversation createGroupConversation(Long groupId) {
        return conversationRepositoryPort.findByGroupId(groupId)
                .orElseGet(() -> {
                    Conversation conversation = Conversation.builder()
                            .type(ConversationType.GROUP)
                            .groupId(groupId)
                            .unreadCount(0)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return conversationRepositoryPort.save(conversation);
                });
    }

    @Override
    public Conversation getConversation(Long id) {
        return conversationRepositoryPort.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found with id: " + id));
    }

    @Override
    public List<Conversation> getUserConversations(String userId) {
        return conversationRepositoryPort.findUserConversations(userId);
    }

    @Override
    public void deleteConversation(Long id) {
        if (!conversationRepositoryPort.existsById(id)) {
            throw new IllegalArgumentException("Conversation not found with id: " + id);
        }
        conversationRepositoryPort.deleteById(id);
    }

    @Override
    public Conversation markAsRead(Long conversationId) {
        Conversation conversation = getConversation(conversationId);
        
        Conversation updatedConversation = Conversation.builder()
                .id(conversation.getId())
                .type(conversation.getType())
                .receiverId(conversation.getReceiverId())
                .groupId(conversation.getGroupId())
                .lastMessageId(conversation.getLastMessageId())
                .lastMessageAt(conversation.getLastMessageAt())
                .unreadCount(0)
                .createdAt(conversation.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();
                
        return conversationRepositoryPort.save(updatedConversation);
    }
}
