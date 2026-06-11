package secretchat.chatservice.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import secretchat.chatservice.application.port.out.FriendRepositoryPort;
import secretchat.chatservice.domain.model.Friend;
import secretchat.chatservice.infrastructure.persistence.entity.FriendEntity;
import secretchat.chatservice.infrastructure.persistence.mapper.FriendMapper;
import secretchat.chatservice.infrastructure.persistence.repository.FriendRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FriendRepositoryAdapter implements FriendRepositoryPort {

    private final FriendRepository friendRepository;
    private final FriendMapper friendMapper;

    @Override
    public Friend save(Friend friend) {
        FriendEntity entity = friendMapper.toEntity(friend);
        FriendEntity saved = friendRepository.save(entity);
        return friendMapper.toDomain(saved);
    }

    @Override
    public Optional<Friend> findByUserIdAndFriendId(String userId, String friendId) {
        return friendRepository.findByUserIdAndFriendId(userId, friendId)
                .map(friendMapper::toDomain);
    }

    @Override
    public Optional<Friend> findBetweenUsers(String firstUserId, String secondUserId) {
        return friendRepository.findByUserIdAndFriendId(firstUserId, secondUserId)
                .or(() -> friendRepository.findByUserIdAndFriendId(secondUserId, firstUserId))
                .map(friendMapper::toDomain);
    }

    @Override
    public Optional<Friend> findById(String id) {
        return friendRepository.findById(id).map(friendMapper::toDomain);
    }

    @Override
    public List<Friend> findByUserId(String userId) {
        return friendRepository.findByUserId(userId).stream()
                .map(friendMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Friend> findByUserIdOrFriendId(String userId) {
        return friendRepository.findByUserIdOrFriendId(userId, userId).stream()
                .map(friendMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Friend> findByFriendId(String friendId) {
        return friendRepository.findByFriendId(friendId).stream()
                .map(friendMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByUserIdAndFriendId(String userId, String friendId) {
        return friendRepository.existsByUserIdAndFriendId(userId, friendId);
    }

    @Override
    public void deleteByUserIdAndFriendId(String userId, String friendId) {
        friendRepository.deleteByUserIdAndFriendId(userId, friendId);
    }

    @Override
    public void delete(Friend friend) {
        friendRepository.deleteById(friend.getId());
    }
}
