package secretchat.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Maps the BE RegisterResponse:
 * {
 *   "id": "...",
 *   "username": "...",
 *   "email": "...",
 *   "fullName": "...",
 *   "status": "ACTIVE"
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegisterResponse {
    private boolean success;
    private String message;
    private String id;
    private String username;
    private String email;
    private String fullName;
    private String status;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
