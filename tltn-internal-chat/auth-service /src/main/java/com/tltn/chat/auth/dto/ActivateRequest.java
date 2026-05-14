package com.tltn.chat.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ActivateRequest {
    @NotBlank
    private String employeeId;
    @NotBlank
    private String defaultPassword;
    @NotBlank
    private String newPassword;
}
