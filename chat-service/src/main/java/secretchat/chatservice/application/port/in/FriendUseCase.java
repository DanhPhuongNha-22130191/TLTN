package secretchat.chatservice.application.port.in;

import secretchat.chatservice.domain.model.Friend;

import java.util.List;

public interface FriendUseCase {
    Friend addFriendByUsername(String userId, String username);
    List<Friend> getFriends(String userId);
    void removeFriend(String userId, String friendId);
}
