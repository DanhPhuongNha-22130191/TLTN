package secretchat.userservice.application.dto;

public record RegisterCommand(
        String username,
        String email,
        String password,
        String confirmPassword,
        String fullName,
        String phoneNumber
) {}
