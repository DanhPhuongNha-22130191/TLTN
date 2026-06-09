package secretchat.chatservice.api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import secretchat.chatservice.api.mapper.MessageApiMapper;
import secretchat.chatservice.api.request.SendMessageRequest;
import secretchat.chatservice.api.response.MessageResponse;
import secretchat.chatservice.api.request.TypingRequest;
import secretchat.chatservice.application.port.in.MessageUseCase;
import secretchat.chatservice.domain.model.Message;
import secretchat.chatservice.api.realtime.MessageRealtimePublisher;

@Controller
@RequiredArgsConstructor
public class MessageWebSocketController {

    private final MessageUseCase messageUseCase;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageRealtimePublisher realtimePublisher;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload SendMessageRequest request) {
        Message message = messageUseCase.sendMessage(MessageApiMapper.toCommand(request));
        realtimePublisher.publish(message);
    }

    @MessageMapping("/chat.typing")
    public void typing(@Payload TypingRequest request) {
        if (request.getConversationId() == null || request.getUserId() == null) {
            return;
        }
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + request.getConversationId() + "/typing",
                request);
    }
}
