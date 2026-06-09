package secretchat.userservice.api.dto;

import secretchat.userservice.application.dto.UserResult;

public record RegisterResponse(
        boolean success,
        String message,
        String userId,
        String username,
        String email,
        String status
) {
    public static RegisterResponse from(UserResult result) {
        return new RegisterResponse(
                true,
                "Registration successful",
                result.keycloakUserId(),
                result.username(),
                result.email(),
                result.status()
        );
    }
}
