package secretchat.chatservice.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import secretchat.chatservice.domain.model.UserProfile;
import secretchat.chatservice.infrastructure.persistence.entity.UserProfileEntity;

@Component
public class UserProfileMapper {

    public UserProfileEntity toEntity(UserProfile domain) {
        if (domain == null) {
            return null;
        }
        return UserProfileEntity.builder()
                .id(domain.getId())
                .username(domain.getUsername())
                .externalSub(domain.getExternalSub())
                .email(domain.getEmail())
                .displayName(domain.getDisplayName())
                .avatarUrl(domain.getAvatarUrl())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    public UserProfile toDomain(UserProfileEntity entity) {
        if (entity == null) {
            return null;
        }
        return UserProfile.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .externalSub(entity.getExternalSub())
                .email(entity.getEmail())
                .displayName(entity.getDisplayName())
                .avatarUrl(entity.getAvatarUrl())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
