package secretchat.userservice.application.port;

import secretchat.userservice.application.dto.LoginResult;

public interface KeycloakTokenPort {
    LoginResult login(String username, String password);
    LoginResult refreshToken(String refreshToken);
    void logout(String refreshToken);
}
