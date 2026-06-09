package secretchat.chatservice.api.request;

import lombok.Data;

@Data
public class TypingRequest {
    private Long conversationId;
    private String userId;
    private String username;
    private boolean typing;
}
