package com.tltn.chat.message.service;

import com.tltn.chat.message.model.Conversation;
import com.tltn.chat.message.model.ChatType;
import com.tltn.chat.message.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;

    public Conversation getOrCreatePersonalConversation(String user1Id, String user2Id) {
        Optional<Conversation> existing = conversationRepository.findPersonalConversation(user1Id, user2Id);
        
        return existing.orElseGet(() -> {
            Conversation conversation = Conversation.builder()
                    .type(ChatType.PERSONAL)
                    .participantIds(Arrays.asList(user1Id, user2Id))
                    .createdAt(LocalDateTime.now())
                    .lastActivityAt(LocalDateTime.now())
                    .build();
            return conversationRepository.save(conversation);
        });
    }

    public Conversation createGroupConversation(String name, List<String> participantIds, String creatorId) {
        if (!participantIds.contains(creatorId)) {
            participantIds.add(creatorId);
        }
        
        Conversation conversation = Conversation.builder()
                .type(ChatType.GROUP)
                .name(name)
                .participantIds(participantIds)
                .createdAt(LocalDateTime.now())
                .lastActivityAt(LocalDateTime.now())
                .build();
                
        return conversationRepository.save(conversation);
    }
    
    public Conversation getConversation(String conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
    }
    
    public List<Conversation> getUserConversations(String userId) {
        return conversationRepository.findByParticipantIdsContaining(userId);
    }
    
    public void updateLastMessage(String conversationId, String messageId) {
        Conversation conv = getConversation(conversationId);
        conv.setLastMessageId(messageId);
        conv.setLastActivityAt(LocalDateTime.now());
        conversationRepository.save(conv);
    }
}
