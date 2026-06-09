package secretchat.chatservice.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import secretchat.chatservice.application.port.in.UserProfileUseCase;
import secretchat.chatservice.application.port.out.UserProfileRepositoryPort;
import secretchat.chatservice.domain.model.UserProfile;
import secretchat.chatservice.infrastructure.client.UserServiceClient;
import secretchat.chatservice.infrastructure.client.dto.UserServiceUserResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService implements UserProfileUseCase {

    private final UserProfileRepositoryPort userProfileRepositoryPort;
    private final UserServiceClient userServiceClient;

    @Override
    public UserProfile createOrUpdateProfile(UserProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("User profile is required");
        }
        if (profile.getId() == null) {
            throw new IllegalArgumentException("User ID is required for profile creation");
        }

        userProfileRepositoryPort.findByUsername(profile.getUsername())
                .filter(existing -> !existing.getId().equals(profile.getId()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Username is already taken: " + profile.getUsername());
                });

        UserProfile updatedProfile = UserProfile.builder()
                .id(profile.getId())
                .username(profile.getUsername())
                .externalSub(profile.getExternalSub())
                .email(profile.getEmail())
                .displayName(profile.getDisplayName())
                .avatarUrl(profile.getAvatarUrl())
                .createdAt(profile.getCreatedAt() != null ? profile.getCreatedAt() : LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return userProfileRepositoryPort.save(updatedProfile);
    }


    private UserProfile syncProfile(UserServiceUserResponse userDto) {
        Optional<UserProfile> existing = userProfileRepositoryPort.findByExternalSub(userDto.keycloakUserId());
        if (existing.isPresent()) {
            UserProfile profile = existing.get();
            boolean changed = !Objects.equals(userDto.username(), profile.getUsername())
                    || !Objects.equals(userDto.email(), profile.getEmail())
                    || !Objects.equals(userDto.fullName(), profile.getDisplayName())
                    || !Objects.equals(userDto.avatar(), profile.getAvatarUrl());
            if (changed) {
                UserProfile updated = UserProfile.builder()
                        .id(profile.getId())
                        .username(userDto.username())
                        .email(userDto.email())
                        .displayName(userDto.fullName())
                        .avatarUrl(userDto.avatar())
                        .externalSub(profile.getExternalSub())
                        .createdAt(profile.getCreatedAt())
                        .updatedAt(LocalDateTime.now())
                        .build();
                return userProfileRepositoryPort.save(updated);
            }
            return profile;
        } else {
            UserProfile newProfile = UserProfile.builder()
                    .id(userDto.keycloakUserId())
                    .username(userDto.username())
                    .email(userDto.email())
                    .displayName(userDto.fullName())
                    .avatarUrl(userDto.avatar())
                    .externalSub(userDto.keycloakUserId())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            return userProfileRepositoryPort.save(newProfile);
        }
    }

    @Override
    public Optional<UserProfile> getProfileById(String userId) {
        Optional<UserProfile> localProfile = userProfileRepositoryPort.findById(userId);
        if (localProfile.isPresent()) {
            try {
                return userServiceClient.getUserByKeycloakId(localProfile.get().getExternalSub())
                        .map(this::syncProfile);
            } catch (Exception e) {
                log.warn("Failed to fetch profile from user-service for userId {}, falling back to local DB", userId, e);
                return localProfile;
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<UserProfile> getProfileByUsername(String username) {
        try {
            return userServiceClient.getUserByUsername(username)
                    .map(this::syncProfile);
        } catch (Exception e) {
            log.warn("Failed to fetch profile from user-service for username {}, falling back to local DB", username, e);
            return userProfileRepositoryPort.findByUsername(username);
        }
    }

    @Override
    public Optional<UserProfile> getProfileByUsernameStrict(String username) {
        return userServiceClient.getUserByUsername(username)
                .map(this::syncProfile);
    }

    @Override
    public Optional<UserProfile> getProfileByExternalSub(String externalSub) {
        try {
            return userServiceClient.getUserByKeycloakId(externalSub)
                    .map(this::syncProfile);
        } catch (Exception e) {
            log.warn("Failed to fetch profile from user-service for externalSub {}, falling back to local DB", externalSub, e);
            return userProfileRepositoryPort.findByExternalSub(externalSub);
        }
    }

    @Override
    public List<UserProfile> getProfilesByIds(List<String> userIds) {
        return userProfileRepositoryPort.findByIds(userIds);
    }
}
