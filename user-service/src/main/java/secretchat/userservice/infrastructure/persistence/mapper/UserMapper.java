package secretchat.userservice.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import secretchat.userservice.domain.enums.UserStatus;
import secretchat.userservice.domain.model.User;
import secretchat.userservice.domain.valueobject.Email;
import secretchat.userservice.domain.valueobject.FullName;
import secretchat.userservice.domain.valueobject.KeycloakUserId;
import secretchat.userservice.domain.valueobject.PhoneNumber;
import secretchat.userservice.infrastructure.persistence.entity.UserEntity;

@Component
public class UserMapper {

    public UserEntity toEntity(User user) {
        UserEntity entity = new UserEntity();
        entity.setKeycloakUserId(user.getKeycloakUserId().getValue());
        entity.setUsername(user.getUsername());
        entity.setEmail(user.getEmail().getValue());
        entity.setFullName(user.getFullName() != null ? user.getFullName().getValue() : null);
        entity.setAvatar(user.getAvatar());
        entity.setPhoneNumber(user.getPhoneNumber() != null ? user.getPhoneNumber().getValue() : null);
        entity.setStatus(user.getStatus().name());
        entity.setCreatedAt(user.getCreatedAt());
        return entity;
    }

    public User toDomain(UserEntity entity) {
        return User.builder()
                .keycloakUserId(new KeycloakUserId(entity.getKeycloakUserId()))
                .username(entity.getUsername())
                .email(new Email(entity.getEmail()))
                .fullName(entity.getFullName() != null ? new FullName(entity.getFullName()) : null)
                .avatar(entity.getAvatar())
                .phoneNumber(entity.getPhoneNumber() != null ? new PhoneNumber(entity.getPhoneNumber()) : null)
                .status(UserStatus.fromValue(entity.getStatus()))
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
