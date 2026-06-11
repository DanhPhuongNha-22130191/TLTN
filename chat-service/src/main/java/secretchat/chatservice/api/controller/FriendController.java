package secretchat.chatservice.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import secretchat.chatservice.api.mapper.FriendApiMapper;
import secretchat.chatservice.api.request.AddFriendRequest;
import secretchat.chatservice.api.response.FriendResponse;
import secretchat.chatservice.application.port.in.FriendUseCase;
import secretchat.chatservice.application.port.in.UserProfileUseCase;
import secretchat.chatservice.domain.model.Friend;
import secretchat.chatservice.domain.model.UserProfile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendUseCase friendUseCase;
    private final UserProfileUseCase userProfileUseCase;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping
    public ResponseEntity<FriendResponse> sendRequest(
            @Valid @RequestBody AddFriendRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String currentUserId = currentUserId(jwt);
        Friend friendRequest = friendUseCase.sendFriendRequest(
                currentUserId, request.getUsername());
        FriendResponse response = responseForCounterpart(
                friendRequest, friendRequest.getFriendId());
        messagingTemplate.convertAndSend(
                "/topic/user/" + friendRequest.getFriendId() + "/friend-requests",
                responseForCounterpart(friendRequest, friendRequest.getUserId()));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<FriendResponse>> getFriends(
            @PathVariable String userId,
            @AuthenticationPrincipal Jwt jwt) {
        requireCurrentUser(userId, jwt);
        List<Friend> friends = friendUseCase.getFriends(userId);
        List<String> friendIds = friends.stream()
                .map(Friend::getFriendId)
                .distinct()
                .toList();
        Map<String, String> usernameById = usernamesById(friendIds);
        return ResponseEntity.ok(friends.stream()
                .map(friend -> FriendApiMapper.toResponse(
                        friend, usernameById.get(friend.getFriendId())))
                .toList());
    }

    @GetMapping("/requests/incoming/{userId}")
    public ResponseEntity<List<FriendResponse>> getIncomingRequests(
            @PathVariable String userId,
            @AuthenticationPrincipal Jwt jwt) {
        requireCurrentUser(userId, jwt);
        List<Friend> requests = friendUseCase.getIncomingRequests(userId);
        Map<String, String> usernameById = usernamesById(requests.stream()
                .map(Friend::getUserId)
                .distinct()
                .toList());
        return ResponseEntity.ok(requests.stream()
                .map(request -> responseForCounterpart(
                        request, request.getUserId(), usernameById.get(request.getUserId())))
                .toList());
    }

    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<FriendResponse> acceptRequest(
            @PathVariable String requestId,
            @RequestParam String userId,
            @AuthenticationPrincipal Jwt jwt) {
        requireCurrentUser(userId, jwt);
        Friend accepted = friendUseCase.acceptRequest(requestId, userId);
        String requesterId = accepted.getUserId();
        FriendResponse recipientResponse = responseForCounterpart(accepted, requesterId);
        messagingTemplate.convertAndSend(
                "/topic/user/" + userId + "/friends", recipientResponse);
        messagingTemplate.convertAndSend(
                "/topic/user/" + requesterId + "/friends",
                responseForCounterpart(accepted, userId));
        return ResponseEntity.ok(recipientResponse);
    }

    @DeleteMapping("/requests/{requestId}")
    public ResponseEntity<Void> rejectRequest(
            @PathVariable String requestId,
            @RequestParam String userId,
            @AuthenticationPrincipal Jwt jwt) {
        requireCurrentUser(userId, jwt);
        friendUseCase.rejectRequest(requestId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/user/{userId}/{friendId}")
    public ResponseEntity<Void> removeFriend(
            @PathVariable String userId,
            @PathVariable String friendId,
            @AuthenticationPrincipal Jwt jwt) {
        requireCurrentUser(userId, jwt);
        friendUseCase.removeFriend(userId, friendId);
        return ResponseEntity.noContent().build();
    }

    private Map<String, String> usernamesById(List<String> ids) {
        return userProfileUseCase.getProfilesByIds(ids).stream()
                .collect(Collectors.toMap(UserProfile::getId, UserProfile::getUsername));
    }

    private String currentUserId(Jwt jwt) {
        return userProfileUseCase.getProfileByExternalSub(jwt.getSubject())
                .map(UserProfile::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "Current chat profile was not found"));
    }

    private void requireCurrentUser(String userId, Jwt jwt) {
        if (!currentUserId(jwt).equals(userId)) {
            throw new IllegalArgumentException(
                    "You cannot manage another user's friend relationships");
        }
    }

    private FriendResponse responseForCounterpart(Friend friend, String counterpartId) {
        String username = userProfileUseCase.getProfileById(counterpartId)
                .map(UserProfile::getUsername)
                .orElse(null);
        return responseForCounterpart(friend, counterpartId, username);
    }

    private FriendResponse responseForCounterpart(
            Friend friend, String counterpartId, String username) {
        String ownerId = counterpartId.equals(friend.getUserId())
                ? friend.getFriendId() : friend.getUserId();
        Friend normalized = Friend.builder()
                .id(friend.getId())
                .userId(ownerId)
                .friendId(counterpartId)
                .status(friend.getStatus())
                .createdAt(friend.getCreatedAt())
                .build();
        return FriendApiMapper.toResponse(normalized, username);
    }
}
