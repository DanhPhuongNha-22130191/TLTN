package com.tltn.chat.message.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessagePayload {
    private String conversationId; // if replying to an existing conversation
    private String senderId;
    private String content;
    private String type;
    
    // For personal chat initiation
    private String recipientId; 
}
