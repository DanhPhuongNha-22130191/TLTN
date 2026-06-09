package secretchat.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversationResponse {
    private String id;
    private String type;
    private String senderId;
    private String receiverId;
    private String groupId;
    private String lastMessageId;
    private String lastMessageAt;
    private int unreadCount;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getLastMessageId() { return lastMessageId; }
    public void setLastMessageId(String lastMessageId) { this.lastMessageId = lastMessageId; }

    public String getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(String lastMessageAt) { this.lastMessageAt = lastMessageAt; }

    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
}
