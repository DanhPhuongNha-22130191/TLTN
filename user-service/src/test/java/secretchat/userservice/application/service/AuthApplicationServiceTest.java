package secretchat.userservice.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import secretchat.userservice.application.dto.ForgotPasswordCommand;
import secretchat.userservice.application.dto.RegisterCommand;
import secretchat.userservice.application.port.KeycloakTokenPort;
import secretchat.userservice.application.port.KeycloakUserPort;
import secretchat.userservice.application.port.MailboxPort;
import secretchat.userservice.domain.enums.UserStatus;
import secretchat.userservice.domain.model.User;
import secretchat.userservice.domain.repository.UserRepository;
import secretchat.userservice.domain.valueobject.Email;
import secretchat.userservice.domain.valueobject.KeycloakUserId;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuthApplicationServiceTest {
    private UserRepository userRepository;
    private KeycloakUserPort keycloakUserPort;
    private MailboxPort mailboxPort;
    private AuthApplicationService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        keycloakUserPort = mock(KeycloakUserPort.class);
        mailboxPort = mock(MailboxPort.class);
        service = new AuthApplicationService(
                userRepository,
                keycloakUserPort,
                mock(KeycloakTokenPort.class),
                mailboxPort,
                "gitlab.handbook.local",
                "http://localhost:8085/");
    }

    @Test
    void registerCreatesInternalMailboxAndUsesItForTheAccount() {
        RegisterCommand command = new RegisterCommand(
                "johndoe", "password123", "password123", "John Doe", "0912345678");
        when(keycloakUserPort.createUser(
                "johndoe", "johndoe@gitlab.handbook.local", "password123", "John Doe"))
                .thenReturn("keycloak-user-id");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.register(command);

        verify(mailboxPort).createMailbox(
                org.mockito.ArgumentMatchers.eq("johndoe@gitlab.handbook.local"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("John Doe"));
        assertEquals("johndoe@gitlab.handbook.local", result.user().email());
        assertEquals("http://localhost:8085/", result.webmailUrl());
        assertNotNull(result.mailboxPassword());
    }

    @Test
    void forgotPasswordSendsResetEmailForExistingAccount() {
        Email email = new Email("member@example.com");
        User user = User.builder()
                .keycloakUserId(new KeycloakUserId("keycloak-user-id"))
                .username("member")
                .email(email)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        service.forgotPassword(new ForgotPasswordCommand("member@example.com"));

        verify(keycloakUserPort).sendPasswordResetEmail("keycloak-user-id");
    }

    @Test
    void forgotPasswordDoesNotRevealMissingAccount() {
        Email email = new Email("missing@example.com");
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        service.forgotPassword(new ForgotPasswordCommand("missing@example.com"));

        verify(keycloakUserPort, never()).sendPasswordResetEmail(
                org.mockito.ArgumentMatchers.anyString());
    }
}
