package com.tltn.chat.auth.dto;

import lombok.Data;

@Data
public class CreateAccountRequest {
    private String employeeId;
    private String username;
    private String defaultPassword;
}
