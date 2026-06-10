package secretchat.userservice.application.usecase;

import secretchat.userservice.application.dto.ForgotPasswordCommand;
import secretchat.userservice.application.dto.LoginCommand;
import secretchat.userservice.application.dto.LoginResult;
import secretchat.userservice.application.dto.LogoutCommand;
import secretchat.userservice.application.dto.RegisterCommand;
import secretchat.userservice.application.dto.RegistrationResult;
import secretchat.userservice.application.dto.UserResult;

import secretchat.userservice.application.dto.RefreshTokenCommand;

public interface AuthUseCase {
    RegistrationResult register(RegisterCommand command);
    LoginResult login(LoginCommand command);
    void forgotPassword(ForgotPasswordCommand command);
    LoginResult refreshToken(RefreshTokenCommand command);
    void logout(LogoutCommand command);
}
