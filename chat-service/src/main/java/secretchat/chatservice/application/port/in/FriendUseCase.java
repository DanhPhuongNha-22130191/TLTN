package secretchat.chatservice.application.port.in;

import secretchat.chatservice.domain.model.Friend;

import java.util.List;

public interface FriendUseCase {
    Friend sendFriendRequest(String userId, String username);
    List<Friend> getFriends(String userId);
    List<Friend> getIncomingRequests(String userId);
    Friend acceptRequest(String requestId, String userId);
    void rejectRequest(String requestId, String userId);
    void removeFriend(String userId, String friendId);
}
