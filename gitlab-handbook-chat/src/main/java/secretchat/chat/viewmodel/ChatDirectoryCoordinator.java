package secretchat.chat.viewmodel;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import secretchat.chat.service.ChatService;
import secretchat.common.exception.ApiException;
import secretchat.dto.request.AddFriendRequest;
import secretchat.dto.response.ConversationResponse;
import secretchat.dto.response.FriendResponse;
import secretchat.dto.response.GroupResponse;
import secretchat.dto.response.UserResponse;
import secretchat.util.IdUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Owns the chat directory, its caches, and friend/group list loading.
 */
final class ChatDirectoryCoordinator {
    private static final System.Logger LOGGER =
            System.getLogger(ChatDirectoryCoordinator.class.getName());

    private final ChatService chatService;
    private final Supplier<String> tokenSupplier;
    private final Supplier<String> currentUserIdSupplier;
    private final Consumer<String> errorConsumer;
    private final Consumer<String> notificationConsumer;
    private final Consumer<String> removedFriendConsumer;

    private final Map<String, UserResponse> usersByName = new HashMap<>();
    private final Map<String, String> userIdsByDisplayName = new HashMap<>();
    private final Map<String, GroupResponse> groupsByName = new HashMap<>();
    private final Map<String, ConversationResponse> groupConversations = new HashMap<>();
    private final Map<String, ConversationResponse> personalConversations = new HashMap<>();
    private final ObservableList<String> privateChats = FXCollections.observableArrayList();
    private final ObservableList<String> groupChats = FXCollections.observableArrayList();

    ChatDirectoryCoordinator(
            ChatService chatService,
            Supplier<String> tokenSupplier,
            Supplier<String> currentUserIdSupplier,
            Consumer<String> errorConsumer,
            Consumer<String> notificationConsumer,
            Consumer<String> removedFriendConsumer) {
        this.chatService = chatService;
        this.tokenSupplier = tokenSupplier;
        this.currentUserIdSupplier = currentUserIdSupplier;
        this.errorConsumer = errorConsumer;
        this.notificationConsumer = notificationConsumer;
        this.removedFriendConsumer = removedFriendConsumer;
    }

    void loadData() {
        Platform.runLater(() -> {
            privateChats.clear();
            usersByName.clear();
            userIdsByDisplayName.clear();
            UserResponse aiUser = new UserResponse();
            aiUser.setId("AI_ASSISTANT");
            aiUser.setUsername("TRỢ LÝ AI");
            aiUser.setFullName("TRỢ LÝ AI");
            usersByName.put("TRỢ LÝ AI", aiUser);
            userIdsByDisplayName.put("TRỢ LÝ AI", "AI_ASSISTANT");
        });

        CompletableFuture.runAsync(() -> {
            try {
                ConversationResponse[] conversations = chatService.getUserConversations(
                        currentUserIdSupplier.get(), tokenSupplier.get());
                Platform.runLater(() -> {
                    groupChats.clear();
                    groupsByName.clear();
                });
                if (conversations != null) {
                    for (ConversationResponse conversation : conversations) {
                        cacheConversation(conversation);
                    }
                }
                loadFriends();
            } catch (Exception error) {
                LOGGER.log(System.Logger.Level.ERROR, "Lỗi tải danh sách cuộc hội thoại", error);
                Platform.runLater(() -> errorConsumer.accept(
                        "Lỗi tải cuộc hội thoại: " + error.getMessage()));
            }
        });
    }

    String getUserDisplayName(String userId) {
        if (userId != null && userId.equals(currentUserIdSupplier.get())) return "Bạn";

        for (Map.Entry<String, String> entry : userIdsByDisplayName.entrySet()) {
            if (entry.getValue().equals(userId)) return entry.getKey();
        }
        for (UserResponse user : usersByName.values()) {
            if (user.getId() != null && user.getId().equals(userId)) {
                return user.getFullName() != null && !user.getFullName().isBlank()
                        ? user.getFullName() : user.getUsername();
            }
        }

        String displayName = fetchUserDisplayName(userId);
        UserResponse user = new UserResponse();
        user.setId(userId);
        user.setKeycloakUserId(userId);
        user.setUsername(displayName);
        user.setFullName(displayName);
        Platform.runLater(() -> {
            usersByName.put(displayName, user);
            userIdsByDisplayName.put(displayName, userId);
        });
        return displayName;
    }

    void addFriend(String username) {
        CompletableFuture.runAsync(() -> {
            try {
                LOGGER.log(System.Logger.Level.INFO, () -> "Gửi lời mời kết bạn tới: " + username);
                AddFriendRequest request = new AddFriendRequest();
                request.setUserId(currentUserIdSupplier.get());
                request.setUsername(username.trim());
                FriendResponse friend = chatService.addFriend(request, tokenSupplier.get());
                if (friend == null || friend.getFriendId() == null) {
                    LOGGER.log(System.Logger.Level.WARNING, () -> "Không nhận được phản hồi hợp lệ khi gửi lời mời tới: " + username);
                    Platform.runLater(() -> errorConsumer.accept(
                            "Không thể thêm bạn. Vui lòng thử lại."));
                    return;
                }
                LOGGER.log(System.Logger.Level.INFO, () -> "Lời mời kết bạn gửi thành công tới: " + username + ", friendId=" + friend.getFriendId());
                Platform.runLater(() -> notificationConsumer.accept(
                        "Đã gửi lời mời kết bạn đến " + username.trim() + "."));
            } catch (Exception error) {
                LOGGER.log(System.Logger.Level.ERROR, "Lỗi khi gửi lời mời kết bạn tới: " + username, error);
                // Also print stacktrace to standard error so it appears in console output
                try { error.printStackTrace(); } catch (Throwable t) { /* ignore */ }
                Platform.runLater(() -> errorConsumer.accept(
                        friendRequestErrorMessage(error)));
            }
        });
    }

    private String friendRequestErrorMessage(Exception error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ApiException apiError) {
                return apiError.getUserMessage();
            }
            current = current.getCause();
        }
        return "Không thể gửi lời mời kết bạn. Vui lòng thử lại.";
    }

    CompletableFuture<java.util.List<FriendResponse>> loadIncomingFriendRequests() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                FriendResponse[] requests = chatService.getIncomingFriendRequests(
                        currentUserIdSupplier.get(), tokenSupplier.get());
                return requests == null ? java.util.List.of() : java.util.List.of(requests);
            } catch (Exception error) {
                throw new java.util.concurrent.CompletionException(error);
            }
        });
    }

    CompletableFuture<Void> acceptFriendRequest(FriendResponse request) {
        return CompletableFuture.runAsync(() -> {
            try {
                FriendResponse accepted = chatService.acceptFriendRequest(
                        request.getId(), currentUserIdSupplier.get(), tokenSupplier.get());
                String displayName = resolveFriendDisplayName(accepted);
                Platform.runLater(() -> {
                    addFriendToList(accepted, displayName);
                    notificationConsumer.accept(
                            "Đã chấp nhận lời mời của " + displayName + ".");
                });
            } catch (Exception error) {
                throw new java.util.concurrent.CompletionException(error);
            }
        });
    }

    CompletableFuture<Void> rejectFriendRequest(FriendResponse request) {
        return CompletableFuture.runAsync(() -> {
            try {
                chatService.rejectFriendRequest(
                        request.getId(), currentUserIdSupplier.get(), tokenSupplier.get());
                Platform.runLater(() -> notificationConsumer.accept(
                        "Đã từ chối lời mời kết bạn."));
            } catch (Exception error) {
                throw new java.util.concurrent.CompletionException(error);
            }
        });
    }

    void removeFriend(String displayName) {
        if (displayName == null || "TRỢ LÝ AI".equals(displayName)) return;
        UserResponse friend = usersByName.get(displayName);
        String friendId = friend != null ? friend.getId() : userIdsByDisplayName.get(displayName);
        if (friendId == null) {
            errorConsumer.accept("Không tìm thấy thông tin bạn bè.");
            return;
        }
        try {
            chatService.removeFriend(
                    currentUserIdSupplier.get(), friendId, tokenSupplier.get());
            Platform.runLater(() -> {
                privateChats.remove(displayName);
                usersByName.remove(displayName);
                userIdsByDisplayName.remove(displayName);
                removedFriendConsumer.accept(displayName);
            });
            notificationConsumer.accept(
                    "Đã xóa " + displayName + " khỏi danh sách bạn bè.");
        } catch (Exception error) {
            errorConsumer.accept("Không thể xóa bạn bè: " + error.getMessage());
        }
    }

    void addFriendToList(FriendResponse friend, String displayName) {
        if (friend == null || friend.getFriendId() == null
                || !"ACCEPTED".equalsIgnoreCase(friend.getStatus())
                || userIdsByDisplayName.containsValue(friend.getFriendId())) {
            return;
        }
        UserResponse friendUser = new UserResponse();
        friendUser.setId(friend.getFriendId());
        friendUser.setKeycloakUserId(friend.getFriendId());
        friendUser.setUsername(friend.getFriendUsername());
        friendUser.setFullName(displayName);
        usersByName.put(displayName, friendUser);
        userIdsByDisplayName.put(displayName, friend.getFriendId());
        if (!privateChats.contains(displayName)) privateChats.add(displayName);
    }

    void performSearch(String keyword) {
        String normalized = keyword == null ? "" : keyword.toLowerCase();
        privateChats.setAll(usersByName.keySet().stream()
                .filter(name -> !"TRỢ LÝ AI".equals(name))
                .filter(name -> name.toLowerCase().contains(normalized))
                .toList());
        groupChats.setAll(groupsByName.keySet().stream()
                .filter(name -> name.toLowerCase().contains(normalized))
                .toList());
    }

    ObservableList<String> privateChats() { return privateChats; }
    ObservableList<String> groupChats() { return groupChats; }
    Map<String, UserResponse> usersByName() { return usersByName; }
    Map<String, String> userIdsByDisplayName() { return userIdsByDisplayName; }
    Map<String, GroupResponse> groupsByName() { return groupsByName; }
    Map<String, ConversationResponse> groupConversations() { return groupConversations; }
    Map<String, ConversationResponse> personalConversations() { return personalConversations; }

    private void cacheConversation(ConversationResponse conversation) {
        String currentUserId = currentUserIdSupplier.get();
        if ("PERSONAL".equalsIgnoreCase(conversation.getType())) {
            String otherUserId = currentUserId.equals(conversation.getSenderId())
                    ? conversation.getReceiverId() : conversation.getSenderId();
            if (otherUserId == null) otherUserId = conversation.getReceiverId();
            if (otherUserId != null && !otherUserId.equals(currentUserId)) {
                personalConversations.put(otherUserId, conversation);
            }
            return;
        }
        if (!"GROUP".equalsIgnoreCase(conversation.getType())
                || conversation.getGroupId() == null) {
            return;
        }
        try {
            GroupResponse group = chatService.getGroupDetails(
                    IdUtils.parseLongId(conversation.getGroupId()), tokenSupplier.get());
            if (group != null) {
                Platform.runLater(() -> {
                    groupChats.add(group.getName());
                    groupsByName.put(group.getName(), group);
                    groupConversations.put(group.getId(), conversation);
                });
            }
        } catch (Exception ignored) {
            // Conversation can reference a group that has already been removed.
        }
    }

    private void loadFriends() {
        try {
            FriendResponse[] friends = chatService.getFriends(
                    currentUserIdSupplier.get(), tokenSupplier.get());
            if (friends == null) return;
            for (FriendResponse friend : friends) {
                String displayName = resolveFriendDisplayName(friend);
                Platform.runLater(() -> addFriendToList(friend, displayName));
            }
        } catch (Exception ignored) {
            // Friend loading should not prevent conversations from being displayed.
        }
    }

    private String resolveFriendDisplayName(FriendResponse friend) {
        String displayName = friend.getFriendUsername();
        try {
            UserResponse user = chatService.getUserById(friend.getFriendId(), tokenSupplier.get());
            if (user != null && user.getFullName() != null && !user.getFullName().isBlank()) {
                displayName = user.getFullName();
            }
        } catch (Exception ignored) {
            // Use the username supplied by the friend response.
        }
        return displayName == null || displayName.isBlank()
                ? "Người dùng " + friend.getFriendId() : displayName;
    }

    private String fetchUserDisplayName(String userId) {
        try {
            UserResponse user = chatService.getUserById(userId, tokenSupplier.get());
            if (user != null && user.getFullName() != null && !user.getFullName().isBlank()) {
                return user.getFullName();
            }
            if (user != null && user.getUsername() != null && !user.getUsername().isBlank()) {
                return user.getUsername();
            }
        } catch (Exception ignored) {
            // A stable fallback keeps message rendering available.
        }
        return "Người dùng " + userId;
    }
}
