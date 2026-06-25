package secretchat.userservice.application.dto;

import secretchat.userservice.domain.enums.UserStatus;

public record ChangeStatusCommand(String keycloakUserId, UserStatus status) {}
