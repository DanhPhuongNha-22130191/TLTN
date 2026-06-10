package secretchat.chatservice.api.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import secretchat.chatservice.api.mapper.MessageApiMapper;
import secretchat.chatservice.api.response.MessageResponse;
import secretchat.chatservice.api.response.MessageReactionResponse;
import secretchat.chatservice.application.port.in.ConversationUseCase;
import secretchat.chatservice.application.port.in.GroupUseCase;
import secretchat.chatservice.application.port.in.MessageReactionUseCase;
import secretchat.chatservice.domain.model.Conversation;
import secretchat.chatservice.domain.model.Message;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class MessageRealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final ConversationUseCase conversationUseCase;
    private final GroupUseCase groupUseCase;
    private final MessageReactionUseCase reactionUseCase;

    public MessageResponse publish(Message message) {
        MessageResponse response = MessageApiMapper.toResponse(message);
        response.setReactions(reactionUseCase.getReactions(message.getId()).stream()
                .map(reaction -> new MessageReactionResponse(
                        reaction.userId(), reaction.emoji()))
                .toList());
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + message.getConversationId(), response);

        Conversation conversation = conversationUseCase.getConversation(message.getConversationId());
        Set<String> recipients = new LinkedHashSet<>();
        recipients.add(message.getSenderId());
        if (conversation.isPersonal()) {
            recipients.add(conversation.getSenderId());
            recipients.add(conversation.getReceiverId());
        } else if (conversation.getGroupId() != null) {
            groupUseCase.getGroupMembers(conversation.getGroupId())
                    .forEach(member -> recipients.add(member.getUserId()));
        }
        recipients.remove(null);
        recipients.forEach(userId -> messagingTemplate.convertAndSend(
                "/topic/user/" + userId + "/messages", response));
        return response;
    }
}
