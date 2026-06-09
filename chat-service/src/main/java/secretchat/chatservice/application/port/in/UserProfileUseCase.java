package secretchat.chatservice.application.port.in;

import secretchat.chatservice.domain.model.UserProfile;

import java.util.List;
import java.util.Optional;

public interface UserProfileUseCase {
    UserProfile createOrUpdateProfile(UserProfile profile);
    Optional<UserProfile> getProfileById(String userId);
    Optional<UserProfile> getProfileByUsername(String username);
    Optional<UserProfile> getProfileByUsernameStrict(String username);
    Optional<UserProfile> getProfileByExternalSub(String externalSub);
    List<UserProfile> getProfilesByIds(List<String> userIds);
}
