package secretchat.userservice.application.dto;

public record RegisterCommand(
        String username,
        String password,
        String confirmPassword,
        String fullName,
        String phoneNumber
) {}
