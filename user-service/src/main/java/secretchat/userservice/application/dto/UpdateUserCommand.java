package secretchat.userservice.application.dto;

public record UpdateUserCommand(
        String keycloakUserId,
        String fullName,
        String avatar,
        String phoneNumber
) {}
