package secretchat.userservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 6) String password,
        @NotBlank String confirmPassword,
        String fullName,
        String phoneNumber
) {}
