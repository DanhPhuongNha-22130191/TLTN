package secretchat.userservice.application.dto;

import secretchat.userservice.domain.model.User;

import java.time.LocalDateTime;

public record UserResult(
        String keycloakUserId,
        String username,
        String email,
        String fullName,
        String avatar,
        String phoneNumber,
        String status,
        LocalDateTime createdAt
) {
    public static UserResult from(User user) {
        return new UserResult(
                user.getKeycloakUserId().getValue(),
                user.getUsername(),
                user.getEmail().getValue(),
                user.getFullName() != null ? user.getFullName().getValue() : null,
                user.getAvatar(),
                user.getPhoneNumber() != null ? user.getPhoneNumber().getValue() : null,
                user.getStatus().name(),
                user.getCreatedAt()
        );
    }
}
