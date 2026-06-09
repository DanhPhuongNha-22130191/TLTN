package secretchat.dto.request;

public class TypingRequest {
    private Long conversationId;
    private String userId;
    private String username;
    private boolean typing;

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public boolean isTyping() { return typing; }
    public void setTyping(boolean typing) { this.typing = typing; }
}
