package secretchat.service;

public class SessionManager {
    private static final SessionManager INSTANCE = new SessionManager();
    
    private String accessToken;
    private String refreshToken;

    private SessionManager() {
    }

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
    
    public void clear() {
        this.accessToken = null;
        this.refreshToken = null;
    }
}
