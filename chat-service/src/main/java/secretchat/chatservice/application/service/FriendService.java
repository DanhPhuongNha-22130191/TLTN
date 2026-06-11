package secretchat.chatservice.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import secretchat.chatservice.application.port.in.FriendUseCase;
import secretchat.chatservice.application.port.in.UserProfileUseCase;
import secretchat.chatservice.application.port.out.FriendRepositoryPort;
import secretchat.chatservice.domain.model.Friend;
import secretchat.chatservice.domain.model.FriendStatus;
import secretchat.chatservice.domain.model.UserProfile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendService implements FriendUseCase {

    private final FriendRepositoryPort friendRepositoryPort;
    private final UserProfileUseCase userProfileUseCase;

    @Override
    @Transactional
    public Friend sendFriendRequest(String userId, String username) {
        requireUserId(userId);
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Friend username is required");
        }

        UserProfile recipient = userProfileUseCase.getProfileByUsernameStrict(username.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found with username: " + username));
        if (recipient.getId().equals(userId)) {
            throw new IllegalArgumentException("Cannot add yourself as a friend");
        }

        friendRepositoryPort.findBetweenUsers(userId, recipient.getId()).ifPresent(existing -> {
            throw new IllegalArgumentException(existing.getStatus() == FriendStatus.ACCEPTED
                    ? "This user is already your friend"
                    : "A friend request is already pending");
        });

        return friendRepositoryPort.save(Friend.builder()
                .userId(userId)
                .friendId(recipient.getId())
                .status(FriendStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Override
    public List<Friend> getFriends(String userId) {
        requireUserId(userId);
        return friendRepositoryPort.findByUserIdOrFriendId(userId).stream()
                .filter(friend -> friend.getStatus() == FriendStatus.ACCEPTED)
                .map(friend -> normalizeForUser(friend, userId))
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                Friend::getFriendId,
                                Function.identity(),
                                (first, ignored) -> first,
                                LinkedHashMap::new),
                        values -> List.copyOf(values.values())));
    }

    @Override
    public List<Friend> getIncomingRequests(String userId) {
        requireUserId(userId);
        return friendRepositoryPort.findByFriendId(userId).stream()
                .filter(friend -> friend.getStatus() == FriendStatus.PENDING)
                .toList();
    }

    @Override
    @Transactional
    public Friend acceptRequest(String requestId, String userId) {
        Friend request = getPendingIncomingRequest(requestId, userId);
        return friendRepositoryPort.save(Friend.builder()
                .id(request.getId())
                .userId(request.getUserId())
                .friendId(request.getFriendId())
                .status(FriendStatus.ACCEPTED)
                .createdAt(request.getCreatedAt())
                .build());
    }

    @Override
    @Transactional
    public void rejectRequest(String requestId, String userId) {
        friendRepositoryPort.delete(getPendingIncomingRequest(requestId, userId));
    }

    @Override
    @Transactional
    public void removeFriend(String userId, String friendId) {
        requireUserId(userId);
        requireUserId(friendId);
        friendRepositoryPort.findBetweenUsers(userId, friendId)
                .filter(friend -> friend.getStatus() == FriendStatus.ACCEPTED)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Friend relationship not found"));
        friendRepositoryPort.deleteByUserIdAndFriendId(userId, friendId);
        friendRepositoryPort.deleteByUserIdAndFriendId(friendId, userId);
    }

    private Friend getPendingIncomingRequest(String requestId, String userId) {
        requireUserId(userId);
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("Friend request ID is required");
        }
        Friend request = friendRepositoryPort.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Friend request not found"));
        if (!userId.equals(request.getFriendId())
                || request.getStatus() != FriendStatus.PENDING) {
            throw new IllegalArgumentException("Friend request cannot be processed");
        }
        return request;
    }

    private Friend normalizeForUser(Friend friend, String userId) {
        String counterpartId = userId.equals(friend.getUserId())
                ? friend.getFriendId() : friend.getUserId();
        return Friend.builder()
                .id(friend.getId())
                .userId(userId)
                .friendId(counterpartId)
                .status(friend.getStatus())
                .createdAt(friend.getCreatedAt())
                .build();
    }

    private void requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }
    }
}
