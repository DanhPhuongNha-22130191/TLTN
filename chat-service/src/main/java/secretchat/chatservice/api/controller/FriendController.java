package secretchat.chatservice.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import secretchat.chatservice.api.mapper.FriendApiMapper;
import secretchat.chatservice.api.mapper.UserProfileApiMapper;
import secretchat.chatservice.api.request.AddFriendRequest;
import secretchat.chatservice.api.response.FriendResponse;
import secretchat.chatservice.api.response.UserProfileResponse;
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

    @PostMapping
    public ResponseEntity<FriendResponse> addFriend(@Valid @RequestBody AddFriendRequest request) {
        Friend friend = friendUseCase.addFriendByUsername(request.getUserId(), request.getUsername());
        String friendUsername = userProfileUseCase.getProfileById(friend.getFriendId())
                .map(UserProfile::getUsername)
                .orElse(null);
        return ResponseEntity.ok(FriendApiMapper.toResponse(friend, friendUsername));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<FriendResponse>> getFriends(@PathVariable String userId) {
        List<Friend> friends = friendUseCase.getFriends(userId);
        List<String> friendIds = friends.stream()
                .map(Friend::getFriendId)
                .distinct()
                .collect(Collectors.toList());

        Map<String, String> usernameById = userProfileUseCase.getProfilesByIds(friendIds).stream()
                .collect(Collectors.toMap(UserProfile::getId, UserProfile::getUsername));

        List<FriendResponse> responses = friends.stream()
                .map(friend -> FriendApiMapper.toResponse(friend, usernameById.get(friend.getFriendId())))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }
}
