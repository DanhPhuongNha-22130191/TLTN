package secretchat.userservice.application.dto;

public record CreateUserCommand(
        String username,
        String email,
        String fullName,
        String phoneNumber,
        String avatar
) {}
