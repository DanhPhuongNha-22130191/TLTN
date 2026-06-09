package secretchat.userservice.api.dto;

public record LoginResponse(
        boolean success,
        String message,
        String accessToken,
        String refreshToken,
        Integer expiresIn
) {}
