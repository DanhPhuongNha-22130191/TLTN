package secretchat.chatservice.api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import secretchat.chatservice.api.mapper.MessageApiMapper;
import secretchat.chatservice.api.request.SendMessageRequest;
import secretchat.chatservice.api.response.MessageResponse;
import secretchat.chatservice.application.port.in.MessageUseCase;
import secretchat.chatservice.domain.model.Message;

@Controller
@RequiredArgsConstructor
public class MessageWebSocketController {

    private final MessageUseCase messageUseCase;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload SendMessageRequest request) {
        Message message = messageUseCase.sendMessage(MessageApiMapper.toCommand(request));
        MessageResponse response = MessageApiMapper.toResponse(message);

        messagingTemplate.convertAndSend(
                "/topic/conversation/" + message.getConversationId(),
                response
        );
    }
}
