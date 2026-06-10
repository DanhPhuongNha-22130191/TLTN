package secretchat.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Maps the BE RegisterResponse:
 * {
 *   "userId": "...",
 *   "username": "...",
 *   "email": "...",
 *   "status": "ACTIVE",
 *   "mailboxPassword": "...",
 *   "webmailUrl": "..."
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegisterResponse {
    private boolean success;
    private String message;
    private String userId;
    private String username;
    private String email;
    private String fullName;
    private String status;
    private String mailboxPassword;
    private String webmailUrl;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMailboxPassword() { return mailboxPassword; }
    public void setMailboxPassword(String mailboxPassword) {
        this.mailboxPassword = mailboxPassword;
    }

    public String getWebmailUrl() { return webmailUrl; }
    public void setWebmailUrl(String webmailUrl) { this.webmailUrl = webmailUrl; }
}
