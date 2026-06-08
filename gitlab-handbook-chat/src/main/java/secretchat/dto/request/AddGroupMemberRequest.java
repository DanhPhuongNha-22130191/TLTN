package secretchat.dto.request;

public class AddGroupMemberRequest {
    private String userId;
    private String invitedBy;
    private String role;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getInvitedBy() { return invitedBy; }
    public void setInvitedBy(String invitedBy) { this.invitedBy = invitedBy; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
