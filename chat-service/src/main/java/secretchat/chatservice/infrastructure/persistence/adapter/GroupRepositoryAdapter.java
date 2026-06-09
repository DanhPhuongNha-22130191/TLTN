package secretchat.chatservice.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import secretchat.chatservice.application.port.out.GroupRepositoryPort;
import secretchat.chatservice.domain.model.Group;
import secretchat.chatservice.infrastructure.persistence.entity.GroupEntity;
import secretchat.chatservice.infrastructure.persistence.repository.GroupRepository;
import secretchat.chatservice.infrastructure.persistence.mapper.GroupMapper;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GroupRepositoryAdapter implements GroupRepositoryPort {

    private final GroupRepository groupRepository;
    private final GroupMapper groupMapper;

    @Override
    public Group save(Group group) {
        GroupEntity entity = groupMapper.toEntity(group);
        GroupEntity savedEntity = groupRepository.save(entity);
        return groupMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Group> findById(Long id) {
        return groupRepository.findById(id)
                .map(groupMapper::toDomain);
    }

    @Override
    public List<Group> findAll() {
        return groupRepository.findAll().stream()
                .map(groupMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(Long id) {
        groupRepository.deleteById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return groupRepository.existsById(id);
    }
}
