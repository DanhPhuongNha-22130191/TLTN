package secretchat.dto.request;

public class CreatePersonalConversationRequest {
    private String senderId;
    private String receiverId;

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }
}
