package secretchat.chatservice.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import secretchat.chatservice.application.port.out.GroupMemberRepositoryPort;
import secretchat.chatservice.domain.model.GroupMember;
import secretchat.chatservice.infrastructure.persistence.entity.GroupMemberEntity;
import secretchat.chatservice.infrastructure.persistence.repository.GroupMemberRepository;
import secretchat.chatservice.infrastructure.persistence.mapper.GroupMemberMapper;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GroupMemberRepositoryAdapter implements GroupMemberRepositoryPort {

    private final GroupMemberRepository groupMemberRepository;
    private final GroupMemberMapper groupMemberMapper;

    @Override
    public GroupMember save(GroupMember groupMember) {
        GroupMemberEntity entity = groupMemberMapper.toEntity(groupMember);
        GroupMemberEntity savedEntity = groupMemberRepository.save(entity);
        return groupMemberMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<GroupMember> findByGroupIdAndUserId(Long groupId, String userId) {
        return groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .map(groupMemberMapper::toDomain);
    }

    @Override
    public List<GroupMember> findByGroupId(Long groupId) {
        return groupMemberRepository.findByGroupId(groupId).stream()
                .map(groupMemberMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<GroupMember> findByUserId(String userId) {
        return groupMemberRepository.findByUserId(userId).stream()
                .map(groupMemberMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(GroupMember groupMember) {
        groupMemberRepository.deleteByGroupIdAndUserId(groupMember.getGroupId(), groupMember.getUserId());
    }

    @Override
    public boolean existsByGroupIdAndUserId(Long groupId, String userId) {
        return groupMemberRepository.existsByGroupIdAndUserId(groupId, userId);
    }
}
