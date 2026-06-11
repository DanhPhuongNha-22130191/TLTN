package secretchat.chatservice.application.port.out;

import secretchat.chatservice.domain.model.Friend;

import java.util.List;
import java.util.Optional;

public interface FriendRepositoryPort {
    Friend save(Friend friend);
    Optional<Friend> findByUserIdAndFriendId(String userId, String friendId);
    Optional<Friend> findBetweenUsers(String firstUserId, String secondUserId);
    Optional<Friend> findById(String id);
    List<Friend> findByUserId(String userId);
    List<Friend> findByUserIdOrFriendId(String userId);
    List<Friend> findByFriendId(String friendId);
    boolean existsByUserIdAndFriendId(String userId, String friendId);
    void deleteByUserIdAndFriendId(String userId, String friendId);
    void delete(Friend friend);
}
