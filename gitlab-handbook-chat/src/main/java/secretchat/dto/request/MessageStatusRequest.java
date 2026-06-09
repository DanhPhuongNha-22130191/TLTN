package secretchat.dto.request;

public class MessageStatusRequest {
    private String userId;
    private String status;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
