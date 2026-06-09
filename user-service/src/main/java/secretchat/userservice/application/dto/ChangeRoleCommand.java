package secretchat.userservice.application.dto;

import secretchat.userservice.domain.enums.UserRole;

public record ChangeRoleCommand(String keycloakUserId, UserRole role) {}
