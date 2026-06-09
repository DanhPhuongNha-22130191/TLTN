package secretchat.chatservice.application.port.out;

import secretchat.chatservice.domain.model.UserProfile;

import java.util.List;
import java.util.Optional;

public interface UserProfileRepositoryPort {
    UserProfile save(UserProfile profile);
    Optional<UserProfile> findById(String userId);
    Optional<UserProfile> findByUsername(String username);
    Optional<UserProfile> findByExternalSub(String externalSub);
    List<UserProfile> findByIds(List<String> ids);
}
