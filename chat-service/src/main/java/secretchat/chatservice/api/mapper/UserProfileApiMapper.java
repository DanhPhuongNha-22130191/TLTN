package secretchat.chatservice.api.mapper;

import secretchat.chatservice.api.request.CreateUserProfileRequest;
import secretchat.chatservice.api.response.UserProfileResponse;
import secretchat.chatservice.domain.model.UserProfile;

public final class UserProfileApiMapper {

    private UserProfileApiMapper() {}

    public static UserProfile toDomain(CreateUserProfileRequest request) {
        if (request == null) {
            return null;
        }
        return UserProfile.builder()
                .id(request.getUserId())
                .username(request.getUsername())
                .externalSub(request.getExternalSub())
                .email(request.getEmail())
                .build();
    }

    public static UserProfileResponse toResponse(UserProfile profile) {
        if (profile == null) {
            return null;
        }
        return UserProfileResponse.builder()
                .id(profile.getId())
                .username(profile.getUsername())
                .externalSub(profile.getExternalSub())
                .email(profile.getEmail())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
