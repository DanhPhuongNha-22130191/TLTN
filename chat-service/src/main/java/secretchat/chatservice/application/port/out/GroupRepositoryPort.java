package secretchat.chatservice.application.port.out;

import secretchat.chatservice.domain.model.Group;

import java.util.List;
import java.util.Optional;

public interface GroupRepositoryPort {
    Group save(Group group);
    Optional<Group> findById(Long id);
    List<Group> findAll();
    void deleteById(Long id);
    boolean existsById(Long id);
}
