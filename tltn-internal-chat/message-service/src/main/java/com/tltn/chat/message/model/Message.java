package com.tltn.chat.message.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "messages")
public class Message {

    @Id
    private String id;
    
    private String conversationId;
    
    private String senderId;
    
    private String content;
    
    private String type; // TEXT, IMAGE, FILE
    
    private LocalDateTime timestamp;
    
}
