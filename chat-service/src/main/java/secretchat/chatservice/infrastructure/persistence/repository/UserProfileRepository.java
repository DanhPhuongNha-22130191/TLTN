package secretchat.chatservice.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import secretchat.chatservice.infrastructure.persistence.entity.UserProfileEntity;

import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfileEntity, String> {
    Optional<UserProfileEntity> findByUsername(String username);
    Optional<UserProfileEntity> findByExternalSub(String externalSub);
}
