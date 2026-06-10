package secretchat.userservice.api.dto;

import secretchat.userservice.application.dto.RegistrationResult;

public record RegisterResponse(
        boolean success,
        String message,
        String userId,
        String username,
        String email,
        String status,
        String mailboxPassword,
        String webmailUrl
) {
    public static RegisterResponse from(RegistrationResult result) {
        var user = result.user();
        return new RegisterResponse(
                true,
                "Registration successful",
                user.keycloakUserId(),
                user.username(),
                user.email(),
                user.status(),
                result.mailboxPassword(),
                result.webmailUrl()
        );
    }
}
