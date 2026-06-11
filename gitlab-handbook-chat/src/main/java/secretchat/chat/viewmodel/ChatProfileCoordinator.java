package secretchat.chat.viewmodel;

import secretchat.chat.service.ChatService;
import secretchat.dto.request.UpdateUserProfileRequest;
import secretchat.dto.response.UserProfileResponse;
import secretchat.dto.response.UserResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Owns asynchronous profile lookup and update fallbacks used by the chat UI.
 */
final class ChatProfileCoordinator {
    private final ChatService chatService;
    private final Supplier<String> tokenSupplier;
    private final Supplier<UserResponse> currentUserSupplier;
    private final Consumer<UserResponse> currentUserUpdater;

    ChatProfileCoordinator(
            ChatService chatService,
            Supplier<String> tokenSupplier,
            Supplier<UserResponse> currentUserSupplier,
            Consumer<UserResponse> currentUserUpdater) {
        this.chatService = chatService;
        this.tokenSupplier = tokenSupplier;
        this.currentUserSupplier = currentUserSupplier;
        this.currentUserUpdater = currentUserUpdater;
    }

    CompletableFuture<UserResponse> loadCurrentUserProfile() {
        UserResponse currentUser = currentUserSupplier.get();
        String keycloakUserId = currentUser == null ? null : currentUser.getKeycloakUserId();
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Không tìm thấy thông tin tài khoản hiện tại."));
        }

        return CompletableFuture.supplyAsync(() -> loadCurrentUserProfile(keycloakUserId))
                .thenApply(profile -> {
                    currentUserUpdater.accept(profile);
                    return profile;
                });
    }

    CompletableFuture<UserResponse> loadGroupMemberProfile(String userId) {
        if (userId == null || userId.isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Không tìm thấy thành viên."));
        }
        return CompletableFuture.supplyAsync(() -> loadGroupMemberProfileSync(userId));
    }

    CompletableFuture<UserResponse> updateCurrentUserProfile(UpdateUserProfileRequest request) {
        UserResponse currentUser = currentUserSupplier.get();
        if (currentUser == null || currentUser.getKeycloakUserId() == null
                || currentUser.getKeycloakUserId().isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Không tìm thấy thông tin tài khoản hiện tại."));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return chatService.updateCurrentUserProfile(request, tokenSupplier.get());
            } catch (Exception error) {
                throw new CompletionException(error);
            }
        }).thenApply(profile -> {
            currentUserUpdater.accept(profile);
            return profile;
        });
    }

    private UserResponse loadCurrentUserProfile(String keycloakUserId) {
        String token = tokenSupplier.get();
        try {
            return chatService.getCurrentUserProfile(token);
        } catch (Exception currentProfileError) {
            try {
                return chatService.getUserById(keycloakUserId, token);
            } catch (Exception idError) {
                UserResponse currentUser = currentUserSupplier.get();
                String username = currentUser == null ? null : currentUser.getUsername();
                if (username == null || username.isBlank()) {
                    throw new CompletionException(idError);
                }
                try {
                    return chatService.getUserByUsername(username, token);
                } catch (Exception usernameError) {
                    usernameError.addSuppressed(idError);
                    usernameError.addSuppressed(currentProfileError);
                    throw new CompletionException(usernameError);
                }
            }
        }
    }

    private UserResponse loadGroupMemberProfileSync(String userId) {
        String token = tokenSupplier.get();
        UserProfileResponse chatProfile = null;
        try {
            try {
                chatProfile = chatService.getUserProfileById(userId, token);
            } catch (Exception ignored) {
                // The Keycloak id lookup below remains a valid fallback.
            }
            String keycloakUserId = chatProfile == null ? null : chatProfile.getExternalSub();
            if (keycloakUserId == null || keycloakUserId.isBlank()) {
                keycloakUserId = userId;
            }
            return chatService.getUserById(keycloakUserId, token);
        } catch (Exception idError) {
            if (chatProfile != null && chatProfile.getUsername() != null
                    && !chatProfile.getUsername().isBlank()) {
                try {
                    return chatService.getUserByUsername(chatProfile.getUsername(), token);
                } catch (Exception usernameError) {
                    usernameError.addSuppressed(idError);
                    throw new CompletionException(usernameError);
                }
            }
            throw new CompletionException(idError);
        }
    }
}
