package secretchat.chatservice.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import secretchat.chatservice.infrastructure.persistence.entity.GroupEntity;

import java.util.List;

@Repository
public interface GroupRepository extends JpaRepository<GroupEntity, Long> {
    @Override
    @EntityGraph(attributePaths = "members")
    List<GroupEntity> findAll();
}
