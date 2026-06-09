package secretchat.chatservice.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import secretchat.chatservice.application.port.out.UserProfileRepositoryPort;
import secretchat.chatservice.domain.model.UserProfile;
import secretchat.chatservice.infrastructure.persistence.entity.UserProfileEntity;
import secretchat.chatservice.infrastructure.persistence.mapper.UserProfileMapper;
import secretchat.chatservice.infrastructure.persistence.repository.UserProfileRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserProfileRepositoryAdapter implements UserProfileRepositoryPort {

    private final UserProfileRepository userProfileRepository;
    private final UserProfileMapper userProfileMapper;

    @Override
    public UserProfile save(UserProfile profile) {
        UserProfileEntity entity = userProfileMapper.toEntity(profile);
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(profile.getCreatedAt() != null ? profile.getCreatedAt() : java.time.LocalDateTime.now());
        }
        entity.setUpdatedAt(java.time.LocalDateTime.now());
        UserProfileEntity saved = userProfileRepository.save(entity);
        return userProfileMapper.toDomain(saved);
    }

    @Override
    public Optional<UserProfile> findById(String userId) {
        return userProfileRepository.findById(userId)
                .map(userProfileMapper::toDomain);
    }

    @Override
    public Optional<UserProfile> findByUsername(String username) {
        return userProfileRepository.findByUsername(username)
                .map(userProfileMapper::toDomain);
    }

    @Override
    public Optional<UserProfile> findByExternalSub(String externalSub) {
        return userProfileRepository.findByExternalSub(externalSub)
                .map(userProfileMapper::toDomain);
    }

    @Override
    public List<UserProfile> findByIds(List<String> ids) {
        return userProfileRepository.findAllById(ids).stream()
                .map(userProfileMapper::toDomain)
                .collect(Collectors.toList());
    }
}
