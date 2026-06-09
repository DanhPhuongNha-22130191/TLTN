package secretchat.chatservice.application.port.in;

import secretchat.chatservice.application.usecase.command.SendMessageCommand;
import secretchat.chatservice.domain.model.Message;

import java.util.List;

public interface MessageUseCase {
    Message sendMessage(SendMessageCommand command);
    List<Message> getChatHistory(Long conversationId);
    Message getMessage(Long messageId);
    Message recallMessage(Long messageId, String userId);
    void deleteMessageForUser(Long messageId, String userId);
}
