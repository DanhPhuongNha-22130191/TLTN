package secretchat.userservice.application.dto;

public record LoginResult(
        boolean success,
        String message,
        String accessToken,
        String refreshToken,
        Integer expiresIn
) {}
