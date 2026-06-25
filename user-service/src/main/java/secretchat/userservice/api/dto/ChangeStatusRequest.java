package secretchat.userservice.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangeStatusRequest(
        @NotBlank String status
) {}
