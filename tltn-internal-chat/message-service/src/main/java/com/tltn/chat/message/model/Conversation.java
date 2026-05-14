package com.tltn.chat.message.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversations")
public class Conversation {
    
    @Id
    private String id;
    
    private ChatType type; // PERSONAL or GROUP
    
    private String name; // Nullable for personal chat
    
    private List<String> participantIds; // User IDs in the chat
    
    private String lastMessageId;
    
    private LocalDateTime lastActivityAt;
    
    private LocalDateTime createdAt;
    
    public void addParticipant(String participantId) {
        if (!participantIds.contains(participantId)) {
            participantIds.add(participantId);
        }
    }
}
