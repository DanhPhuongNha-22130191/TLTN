package secretchat.chatservice.api.mapper;

import secretchat.chatservice.api.response.FriendResponse;
import secretchat.chatservice.domain.model.Friend;

public final class FriendApiMapper {

    private FriendApiMapper() {}

    public static FriendResponse toResponse(Friend friend, String friendUsername) {
        if (friend == null) {
            return null;
        }
        return FriendResponse.builder()
                .id(friend.getId())
                .friendId(friend.getFriendId())
                .friendUsername(friendUsername)
                .status(friend.getStatus().name())
                .createdAt(friend.getCreatedAt())
                .build();
    }
}
