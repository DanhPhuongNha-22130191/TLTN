package secretchat.userservice.domain.repository;

import secretchat.userservice.domain.model.User;
import secretchat.userservice.domain.valueobject.Email;
import secretchat.userservice.domain.valueobject.KeycloakUserId;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findByKeycloakUserId(KeycloakUserId keycloakUserId);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(Email email);

    boolean existsByUsername(String username);

    boolean existsByEmail(Email email);

    List<User> findAll();

    void deleteByKeycloakUserId(KeycloakUserId keycloakUserId);
}
