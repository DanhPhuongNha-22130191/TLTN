package secretchat.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateMemberRoleRequest {
    private String role;

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
