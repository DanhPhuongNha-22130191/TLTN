package secretchat.userservice.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import secretchat.userservice.infrastructure.persistence.entity.UserEntity;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserEntity, String> {

    Optional<UserEntity> findByUsername(String username);

    Optional<UserEntity> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    void deleteByKeycloakUserId(String keycloakUserId);
}
