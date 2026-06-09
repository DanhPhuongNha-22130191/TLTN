package secretchat.userservice.api.dto;

import secretchat.userservice.application.dto.UserResult;

import java.time.LocalDateTime;

public record UserResponse(
        String keycloakUserId,
        String username,
        String email,
        String fullName,
        String avatar,
        String phoneNumber,
        String status,
        LocalDateTime createdAt
) {
    public static UserResponse from(UserResult r) {
        return new UserResponse(r.keycloakUserId(), r.username(), r.email(),
                r.fullName(), r.avatar(), r.phoneNumber(), r.status(), r.createdAt());
    }
}
