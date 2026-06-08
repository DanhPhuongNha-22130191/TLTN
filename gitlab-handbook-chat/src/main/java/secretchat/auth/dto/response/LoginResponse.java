package secretchat.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Maps the BE LoginResponse:
 * {
 *   "accessToken": "...",
 *   "refreshToken": "...",
 *   "tokenType": "Bearer",
 *   "expiresIn": 300
 * }
 * On error the gateway returns an ApiErrorResponse wrapped in a RuntimeException
 * by ApiClient / GlobalExceptionHandler before this DTO is populated.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginResponse {
    private boolean success;
    private String message;
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private int expiresIn;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public int getExpiresIn() { return expiresIn; }
    public void setExpiresIn(int expiresIn) { this.expiresIn = expiresIn; }
}
