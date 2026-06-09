package secretchat.userservice.application.service;

import org.springframework.stereotype.Service;
import secretchat.userservice.application.dto.LoginCommand;
import secretchat.userservice.application.dto.LoginResult;
import secretchat.userservice.application.dto.LogoutCommand;
import secretchat.userservice.application.dto.RegisterCommand;
import secretchat.userservice.application.dto.UserResult;
import secretchat.userservice.application.port.KeycloakTokenPort;
import secretchat.userservice.application.port.KeycloakUserPort;
import secretchat.userservice.application.usecase.AuthUseCase;
import secretchat.userservice.domain.enums.UserStatus;
import secretchat.userservice.domain.exception.UserAlreadyExistsException;
import secretchat.userservice.domain.model.User;
import secretchat.userservice.domain.repository.UserRepository;
import secretchat.userservice.domain.valueobject.Email;
import secretchat.userservice.domain.valueobject.FullName;
import secretchat.userservice.domain.valueobject.KeycloakUserId;
import secretchat.userservice.domain.valueobject.PhoneNumber;

import java.time.LocalDateTime;

@Service
public class AuthApplicationService implements AuthUseCase {

    private final UserRepository userRepository;
    private final KeycloakUserPort keycloakUserPort;
    private final KeycloakTokenPort keycloakTokenPort;

    public AuthApplicationService(UserRepository userRepository,
                                  KeycloakUserPort keycloakUserPort,
                                  KeycloakTokenPort keycloakTokenPort) {
        this.userRepository   = userRepository;
        this.keycloakUserPort = keycloakUserPort;
        this.keycloakTokenPort = keycloakTokenPort;
    }

    @Override
    public UserResult register(RegisterCommand command) {
        if (!command.password().equals(command.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        if (userRepository.existsByUsername(command.username())) {
            throw new UserAlreadyExistsException("Username already exists: " + command.username());
        }
        if (userRepository.existsByEmail(new Email(command.email()))) {
            throw new UserAlreadyExistsException("Email already exists: " + command.email());
        }

        String keycloakId = keycloakUserPort.createUser(
                command.username(), command.email(), command.password(), command.fullName()
        );

        User user = User.builder()
                .keycloakUserId(new KeycloakUserId(keycloakId))
                .username(command.username())
                .email(new Email(command.email()))
                .fullName(FullName.of(command.fullName()))
                .phoneNumber(PhoneNumber.of(command.phoneNumber()))
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        return UserResult.from(userRepository.save(user));
    }

    @Override
    public LoginResult login(LoginCommand command) {
        return keycloakTokenPort.login(command.username(), command.password());
    }

    @Override
    public LoginResult refreshToken(secretchat.userservice.application.dto.RefreshTokenCommand command) {
        return keycloakTokenPort.refreshToken(command.refreshToken());
    }

    @Override
    public void logout(LogoutCommand command) {
        keycloakTokenPort.logout(command.refreshToken());
    }
}
