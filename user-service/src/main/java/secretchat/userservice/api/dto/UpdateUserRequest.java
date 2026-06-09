package secretchat.userservice.api.dto;

public record UpdateUserRequest(
        String fullName,
        String avatar,
        String phoneNumber
) {}
