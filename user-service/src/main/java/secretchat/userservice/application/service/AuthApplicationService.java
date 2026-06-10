package secretchat.userservice.application.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import secretchat.userservice.application.dto.ForgotPasswordCommand;
import secretchat.userservice.application.dto.LoginCommand;
import secretchat.userservice.application.dto.LoginResult;
import secretchat.userservice.application.dto.LogoutCommand;
import secretchat.userservice.application.dto.RegisterCommand;
import secretchat.userservice.application.dto.RegistrationResult;
import secretchat.userservice.application.dto.UserResult;
import secretchat.userservice.application.port.KeycloakTokenPort;
import secretchat.userservice.application.port.KeycloakUserPort;
import secretchat.userservice.application.port.MailboxPort;
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
import java.security.SecureRandom;

@Service
public class AuthApplicationService implements AuthUseCase {
    private static final String PASSWORD_CHARACTERS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
    private static final int MAILBOX_PASSWORD_LENGTH = 16;

    private final UserRepository userRepository;
    private final KeycloakUserPort keycloakUserPort;
    private final KeycloakTokenPort keycloakTokenPort;
    private final MailboxPort mailboxPort;
    private final SecureRandom secureRandom = new SecureRandom();

    private final String mailDomain;
    private final String webmailUrl;

    public AuthApplicationService(UserRepository userRepository,
                                  KeycloakUserPort keycloakUserPort,
                                  KeycloakTokenPort keycloakTokenPort,
                                  MailboxPort mailboxPort,
                                  @Value("${mail.internal.domain}") String mailDomain,
                                  @Value("${mail.internal.webmail-url}") String webmailUrl) {
        this.userRepository   = userRepository;
        this.keycloakUserPort = keycloakUserPort;
        this.keycloakTokenPort = keycloakTokenPort;
        this.mailboxPort = mailboxPort;
        this.mailDomain = mailDomain;
        this.webmailUrl = webmailUrl;
    }

    @Override
    public RegistrationResult register(RegisterCommand command) {
        if (!command.password().equals(command.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        if (userRepository.existsByUsername(command.username())) {
            throw new UserAlreadyExistsException("Username already exists: " + command.username());
        }
        Email internalEmail = new Email(command.username() + "@" + mailDomain);
        if (userRepository.existsByEmail(internalEmail)) {
            throw new UserAlreadyExistsException("Email already exists: " + internalEmail.getValue());
        }

        String mailboxPassword = generateMailboxPassword();
        String keycloakId = null;
        boolean mailboxCreated = false;
        try {
            mailboxPort.createMailbox(
                    internalEmail.getValue(), mailboxPassword, command.fullName());
            mailboxCreated = true;
            keycloakId = keycloakUserPort.createUser(
                    command.username(), internalEmail.getValue(),
                    command.password(), command.fullName());

            User user = User.builder()
                    .keycloakUserId(new KeycloakUserId(keycloakId))
                    .username(command.username())
                    .email(internalEmail)
                    .fullName(FullName.of(command.fullName()))
                    .phoneNumber(PhoneNumber.of(command.phoneNumber()))
                    .status(UserStatus.ACTIVE)
                    .createdAt(LocalDateTime.now())
                    .build();

            return new RegistrationResult(
                    UserResult.from(userRepository.save(user)),
                    mailboxPassword,
                    webmailUrl);
        } catch (RuntimeException error) {
            if (keycloakId != null) {
                try {
                    keycloakUserPort.deleteUser(keycloakId);
                } catch (RuntimeException ignored) {
                    error.addSuppressed(ignored);
                }
            }
            if (mailboxCreated) {
                try {
                    mailboxPort.deleteMailbox(internalEmail.getValue());
                } catch (RuntimeException ignored) {
                    error.addSuppressed(ignored);
                }
            }
            throw error;
        }
    }

    private String generateMailboxPassword() {
        StringBuilder password = new StringBuilder(MAILBOX_PASSWORD_LENGTH);
        for (int index = 0; index < MAILBOX_PASSWORD_LENGTH; index++) {
            password.append(PASSWORD_CHARACTERS.charAt(
                    secureRandom.nextInt(PASSWORD_CHARACTERS.length())));
        }
        return password.toString();
    }

    @Override
    public LoginResult login(LoginCommand command) {
        return keycloakTokenPort.login(command.username(), command.password());
    }

    @Override
    public void forgotPassword(ForgotPasswordCommand command) {
        userRepository.findByEmail(new Email(command.email()))
                .ifPresent(user -> keycloakUserPort.sendPasswordResetEmail(
                        user.getKeycloakUserId().getValue()));
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
