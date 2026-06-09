package secretchat.userservice.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank @Email String email,
        String fullName,
        String phoneNumber,
        String avatar
) {}
