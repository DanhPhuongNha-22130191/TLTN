package secretchat.chatservice.application.port.out;

import secretchat.chatservice.domain.model.Friend;

import java.util.List;
import java.util.Optional;

public interface FriendRepositoryPort {
    Friend save(Friend friend);
    Optional<Friend> findByUserIdAndFriendId(String userId, String friendId);
    List<Friend> findByUserId(String userId);
    boolean existsByUserIdAndFriendId(String userId, String friendId);
    void deleteByUserIdAndFriendId(String userId, String friendId);
}
