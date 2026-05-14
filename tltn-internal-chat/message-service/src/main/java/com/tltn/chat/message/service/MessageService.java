package com.tltn.chat.message.service;

import com.tltn.chat.message.dto.MessagePayload;
import com.tltn.chat.message.model.Conversation;
import com.tltn.chat.message.model.Message;
import com.tltn.chat.message.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationService conversationService;

    public Message processAndSaveMessage(MessagePayload payload) {
        String conversationId = payload.getConversationId();
        
        // If it's a new personal chat and no conversationId is given, we need recipientId
        if (conversationId == null && payload.getRecipientId() != null) {
            Conversation conv = conversationService.getOrCreatePersonalConversation(
                    payload.getSenderId(), 
                    payload.getRecipientId());
            conversationId = conv.getId();
        } else if (conversationId == null) {
            throw new IllegalArgumentException("Conversation ID or Recipient ID must be provided");
        }

        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(payload.getSenderId())
                .content(payload.getContent())
                .type(payload.getType() != null ? payload.getType() : "TEXT")
                .timestamp(LocalDateTime.now())
                .build();

        Message savedMessage = messageRepository.save(message);
        
        conversationService.updateLastMessage(conversationId, savedMessage.getId());
        
        return savedMessage;
    }

    public List<Message> getChatHistory(String conversationId) {
        return messageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
    }
}
