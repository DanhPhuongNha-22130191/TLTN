package secretchat.chatservice.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import secretchat.chatservice.application.port.in.FriendUseCase;
import secretchat.chatservice.application.port.in.UserProfileUseCase;
import secretchat.chatservice.application.port.out.FriendRepositoryPort;
import secretchat.chatservice.domain.model.Friend;
import secretchat.chatservice.domain.model.UserProfile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FriendService implements FriendUseCase {

    private final FriendRepositoryPort friendRepositoryPort;
    private final UserProfileUseCase userProfileUseCase;

    @Override
    @Transactional
    public Friend addFriendByUsername(String userId, String username) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Friend username is required");
        }

        UserProfile friendProfile = userProfileUseCase.getProfileByUsernameStrict(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found with username: " + username));

        if (friendProfile.getId().equals(userId)) {
            throw new IllegalArgumentException("Cannot add yourself as a friend");
        }
        if (friendRepositoryPort.existsByUserIdAndFriendId(userId, friendProfile.getId())) {
            throw new IllegalArgumentException("This user is already your friend");
        }

        Friend friend = Friend.builder()
                .userId(userId)
                .friendId(friendProfile.getId())
                .createdAt(LocalDateTime.now())
                .build();
        Friend saved = friendRepositoryPort.save(friend);

        if (!friendRepositoryPort.existsByUserIdAndFriendId(friendProfile.getId(), userId)) {
            friendRepositoryPort.save(Friend.builder()
                    .userId(friendProfile.getId())
                    .friendId(userId)
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        return saved;
    }

    @Override
    public List<Friend> getFriends(String userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        return friendRepositoryPort.findByUserId(userId);
    }

}
