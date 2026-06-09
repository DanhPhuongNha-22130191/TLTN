package secretchat.chatservice.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserServiceUserResponse(
        String keycloakUserId,
        String username,
        String email,
        String fullName,
        String avatar,
        String phoneNumber,
        String status,
        LocalDateTime createdAt
) {}
