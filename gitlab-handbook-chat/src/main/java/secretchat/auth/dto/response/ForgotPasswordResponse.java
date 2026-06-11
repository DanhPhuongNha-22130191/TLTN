package secretchat.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ForgotPasswordResponse {
    private String message;
    private String webmailUrl;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getWebmailUrl() {
        return webmailUrl;
    }

    public void setWebmailUrl(String webmailUrl) {
        this.webmailUrl = webmailUrl;
    }
}
