package secretchat.userservice.application.dto;

public record UpdateUserCommand(
        String keycloakUserId,
        String username,
        String fullName,
        String avatar,
        String phoneNumber,
        String newPassword
) {}
