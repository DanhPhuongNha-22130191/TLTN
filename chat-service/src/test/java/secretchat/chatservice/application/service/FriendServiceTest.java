package secretchat.chatservice.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import secretchat.chatservice.application.port.in.UserProfileUseCase;
import secretchat.chatservice.application.port.out.FriendRepositoryPort;
import secretchat.chatservice.domain.model.Friend;
import secretchat.chatservice.domain.model.FriendStatus;
import secretchat.chatservice.domain.model.UserProfile;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendServiceTest {

    @Mock private FriendRepositoryPort repository;
    @Mock private UserProfileUseCase profiles;

    private FriendService service;

    @BeforeEach
    void setUp() {
        service = new FriendService(repository, profiles);
    }

    @Test
    void sendsPendingFriendRequest() {
        UserProfile recipient = UserProfile.builder()
                .id("recipient")
                .username("recipient-user")
                .build();
        when(profiles.getProfileByUsernameStrict("recipient-user"))
                .thenReturn(Optional.of(recipient));
        when(repository.findBetweenUsers("sender", "recipient"))
                .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Friend result = service.sendFriendRequest("sender", "recipient-user");

        assertEquals(FriendStatus.PENDING, result.getStatus());
        assertEquals("sender", result.getUserId());
        assertEquals("recipient", result.getFriendId());
    }

    @Test
    void recipientCanAcceptPendingRequest() {
        Friend pending = pendingRequest();
        when(repository.findById("request-1")).thenReturn(Optional.of(pending));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Friend accepted = service.acceptRequest("request-1", "recipient");

        assertEquals(FriendStatus.ACCEPTED, accepted.getStatus());
        assertEquals("request-1", accepted.getId());
    }

    @Test
    void recipientCanRejectPendingRequest() {
        Friend pending = pendingRequest();
        when(repository.findById("request-1")).thenReturn(Optional.of(pending));

        service.rejectRequest("request-1", "recipient");

        ArgumentCaptor<Friend> captor = ArgumentCaptor.forClass(Friend.class);
        verify(repository).delete(captor.capture());
        assertEquals("request-1", captor.getValue().getId());
    }

    private Friend pendingRequest() {
        return Friend.builder()
                .id("request-1")
                .userId("sender")
                .friendId("recipient")
                .status(FriendStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
