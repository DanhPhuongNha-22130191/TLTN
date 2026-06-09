package secretchat.chatservice.application.port.in;

import secretchat.chatservice.application.usecase.command.SendMessageCommand;
import secretchat.chatservice.domain.model.Message;
import secretchat.chatservice.domain.enums.MessageStatus;

import java.util.List;

public interface MessageUseCase {
    Message sendMessage(SendMessageCommand command);
    List<Message> getChatHistory(Long conversationId);
    Message getMessage(Long messageId);
    Message recallMessage(Long messageId, String userId);
    void deleteMessageForUser(Long messageId, String userId);
    Message editMessage(Long messageId, String userId, String content);
    Message setStarred(Long messageId, boolean starred);
    Message setPinned(Long messageId, boolean pinned);
    Message updateStatus(Long messageId, String userId, MessageStatus status);
    List<Message> getPinnedMessages(Long conversationId);
    List<Message> searchMessages(Long conversationId, String query);
}
