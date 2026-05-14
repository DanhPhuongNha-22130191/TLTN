package com.tltn.chat.message.controller;

import com.tltn.chat.message.dto.MessagePayload;
import com.tltn.chat.message.model.Message;
import com.tltn.chat.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class WebSocketChatController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload MessagePayload payload) {
        // Save to DB
        Message savedMessage = messageService.processAndSaveMessage(payload);

        // Broadcast to specific conversation topic
        // Client should subscribe to /topic/conversation/{conversationId}
        messagingTemplate.convertAndSend("/topic/conversation/" + savedMessage.getConversationId(), savedMessage);
        
        // Optionally, if you also want to specifically alert users by their userId (via /user/{userId}/queue/messages)
        // you could do round-robin / explicit sends to users. Generally topic is easier for both group & personal.
    }
}
