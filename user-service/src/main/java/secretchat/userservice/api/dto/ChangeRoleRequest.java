package secretchat.userservice.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ChangeRoleRequest(
        @NotNull
        @Pattern(regexp = "ADMIN|USER", message = "Role must be ADMIN or USER")
        String role
) {}
