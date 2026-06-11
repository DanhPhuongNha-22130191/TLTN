package secretchat.chat.viewmodel;

import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import secretchat.chat.service.ChatService;
import secretchat.chat.service.RealtimeChatService;
import secretchat.dto.request.MessageReactionRequest;
import secretchat.dto.request.TypingRequest;
import secretchat.dto.response.ConversationResponse;
import secretchat.dto.response.FriendResponse;
import secretchat.dto.response.MessageResponse;
import secretchat.dto.response.UserResponse;
import secretchat.util.IdUtils;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Owns realtime subscriptions, incoming message reconciliation, typing, and reactions.
 */
final class ChatRealtimeCoordinator {
    private static final System.Logger LOGGER =
            System.getLogger(ChatRealtimeCoordinator.class.getName());
    private static final Pattern LINK_PATTERN = Pattern.compile("(https?://\\S+|www\\.\\S+)");

    private final ChatService chatService;
    private final RealtimeChatService realtimeService;
    private final ChatDirectoryCoordinator directory;
    private final ChatConversationCoordinator conversations;
    private final Supplier<String> tokenSupplier;
    private final Supplier<String> currentUserIdSupplier;
    private final Supplier<UserResponse> currentUserSupplier;
    private final Supplier<Boolean> applicationActiveSupplier;
    private final Consumer<String> errorConsumer;
    private final BiFunction<MessageResponse, ConversationResponse,
            ChatViewModel.NewMessageEvent> eventFactory;
    private final ObservableList<ChatViewModel.MessageItem> messages;
    private final ObservableList<String> sentFiles;
    private final ObservableList<String> sentLinks;
    private final ObjectProperty<ConversationResponse> activeConversation;
    private final StringProperty typingText;
    private final IntegerProperty conversationVersion;
    private final ObjectProperty<ChatViewModel.NewMessageEvent> newMessageEvent;
    private final Set<String> receivedMessageIds = ConcurrentHashMap.newKeySet();

    ChatRealtimeCoordinator(
            ChatService chatService,
            RealtimeChatService realtimeService,
            ChatDirectoryCoordinator directory,
            ChatConversationCoordinator conversations,
            Supplier<String> tokenSupplier,
            Supplier<String> currentUserIdSupplier,
            Supplier<UserResponse> currentUserSupplier,
            Supplier<Boolean> applicationActiveSupplier,
            Consumer<String> errorConsumer,
            BiFunction<MessageResponse, ConversationResponse,
                    ChatViewModel.NewMessageEvent> eventFactory,
            ObservableList<ChatViewModel.MessageItem> messages,
            ObservableList<String> sentFiles,
            ObservableList<String> sentLinks,
            ObjectProperty<ConversationResponse> activeConversation,
            StringProperty typingText,
            IntegerProperty conversationVersion,
            ObjectProperty<ChatViewModel.NewMessageEvent> newMessageEvent) {
        this.chatService = chatService;
        this.realtimeService = realtimeService;
        this.directory = directory;
        this.conversations = conversations;
        this.tokenSupplier = tokenSupplier;
        this.currentUserIdSupplier = currentUserIdSupplier;
        this.currentUserSupplier = currentUserSupplier;
        this.applicationActiveSupplier = applicationActiveSupplier;
        this.errorConsumer = errorConsumer;
        this.eventFactory = eventFactory;
        this.messages = messages;
        this.sentFiles = sentFiles;
        this.sentLinks = sentLinks;
        this.activeConversation = activeConversation;
        this.typingText = typingText;
        this.conversationVersion = conversationVersion;
        this.newMessageEvent = newMessageEvent;
    }

    void subscribeUserUpdates() {
        String currentUserId = currentUserIdSupplier.get();
        realtimeService.subscribeUserMessages(currentUserId, this::handleMessage)
                .exceptionally(error -> {
                    Platform.runLater(() -> errorConsumer.accept(
                            "Không thể theo dõi tin nhắn realtime: " + rootMessage(error)));
                    return null;
                });
        realtimeService.subscribeFriends(currentUserId, this::handleFriend)
                .exceptionally(error -> {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Không thể theo dõi danh sách bạn bè realtime", error);
                    return null;
                });
    }

    void subscribeConversation(String conversationId) {
        typingText.set(null);
        realtimeService.subscribeTyping(conversationId, event -> {
                    if (event.getUserId() == null
                            || event.getUserId().equals(currentUserIdSupplier.get())) return;
                    Platform.runLater(() -> typingText.set(event.isTyping()
                            ? displayTyping(event.getUsername()) : null));
                })
                .exceptionally(error -> {
                    Platform.runLater(() -> errorConsumer.accept(
                            "Không thể theo dõi trạng thái nhập: " + rootMessage(error)));
                    return null;
                });
    }

    void sendTyping(boolean typing) {
        ConversationResponse conversation = activeConversation.get();
        if (conversation == null || conversation.getId() == null) return;
        TypingRequest request = new TypingRequest();
        request.setConversationId(IdUtils.parseLongId(conversation.getId()));
        request.setUserId(currentUserIdSupplier.get());
        UserResponse currentUser = currentUserSupplier.get();
        request.setUsername(currentUser == null ? null : currentUser.getUsername());
        request.setTyping(typing);
        realtimeService.sendTyping(request);
    }

    void react(ChatViewModel.MessageItem item, String emoji) {
        if (item == null || item.getResponse() == null
                || item.getResponse().getId() == null
                || item.getResponse().getId().startsWith("pending-")) return;
        String currentUserId = currentUserIdSupplier.get();
        String selectedEmoji = emoji != null
                && emoji.equals(item.getReactionFor(currentUserId)) ? null : emoji;
        CompletableFuture.runAsync(() -> {
            try {
                MessageReactionRequest request = new MessageReactionRequest();
                request.setUserId(currentUserId);
                request.setEmoji(selectedEmoji);
                MessageResponse updated = chatService.setMessageReaction(
                        IdUtils.parseLongId(item.getResponse().getId()),
                        request,
                        tokenSupplier.get());
                Platform.runLater(() -> item.update(updated));
            } catch (Exception error) {
                Platform.runLater(() -> errorConsumer.accept(
                        "Không thể thả cảm xúc: " + rootMessage(error)));
            }
        });
    }

    void close() {
        realtimeService.close();
    }

    private void handleFriend(FriendResponse friend) {
        if (friend == null || friend.getFriendId() == null
                || !"ACCEPTED".equalsIgnoreCase(friend.getStatus())) return;
        CompletableFuture.runAsync(() -> {
            String displayName = friend.getFriendUsername();
            try {
                UserResponse user = chatService.getUserById(
                        friend.getFriendId(), tokenSupplier.get());
                if (user != null && user.getFullName() != null
                        && !user.getFullName().isBlank()) {
                    displayName = user.getFullName();
                }
            } catch (Exception ignored) {
                // Use the username supplied by the realtime event.
            }
            if (displayName == null || displayName.isBlank()) {
                displayName = "Người dùng " + friend.getFriendId();
            }
            String resolvedName = displayName;
            Platform.runLater(() -> directory.addFriendToList(friend, resolvedName));
        });
    }

    private void handleMessage(MessageResponse message) {
        if (message == null || message.getConversationId() == null) return;
        String currentUserId = currentUserIdSupplier.get();
        boolean isMe = currentUserId.equals(message.getSenderId());
        ConversationResponse conversation =
                conversations.findConversation(message.getConversationId());
        if (conversation == null) return;
        boolean firstDelivery = message.getId() == null
                || receivedMessageIds.add(message.getId());
        ConversationResponse current = activeConversation.get();
        boolean active = current != null
                && message.getConversationId().equals(current.getId());
        boolean activelyViewed = active && applicationActiveSupplier.get();

        if (!isMe) {
            conversations.updateMessageStatus(
                    message, activelyViewed ? "SEEN" : "DELIVERED");
            if (firstDelivery) updateUnreadAndPublish(message, conversation);
        }
        if (!active) return;

        ChatViewModel.MessageItem existing = conversations.findMessage(message.getId());
        if (existing == null && isMe) {
            existing = conversations.findMatchingPending(message);
        }
        if (existing != null) {
            ChatViewModel.MessageItem target = existing;
            Platform.runLater(() -> {
                if (!isCurrent(message)) return;
                target.update(message);
                conversations.applyPinnedRealtime(message, target);
            });
            return;
        }
        if (!conversations.registerDisplayedMessage(message.getId())) return;
        addIncomingMessage(message, isMe);
    }

    private void updateUnreadAndPublish(
            MessageResponse message, ConversationResponse conversation) {
        Platform.runLater(() -> {
            ConversationResponse current = activeConversation.get();
            boolean open = current != null
                    && message.getConversationId().equals(current.getId());
            if (open) {
                if (conversation.getUnreadCount() != 0) {
                    conversation.setUnreadCount(0);
                    incrementConversationVersion();
                }
            } else {
                conversation.setUnreadCount(conversation.getUnreadCount() + 1);
                incrementConversationVersion();
            }
            newMessageEvent.set(eventFactory.apply(message, conversation));
        });
    }

    private void addIncomingMessage(MessageResponse message, boolean isMe) {
        boolean file = !"TEXT".equalsIgnoreCase(message.getMessageType());
        String content = file && message.getFileName() != null
                ? message.getFileName() : message.getContent();
        String sender = "AI_ASSISTANT".equals(message.getSenderId())
                ? "TRỢ LÝ AI" : directory.getUserDisplayName(message.getSenderId());
        Platform.runLater(() -> {
            if (!isCurrent(message)) return;
            ChatViewModel.MessageItem item = new ChatViewModel.MessageItem(
                    message,
                    sender,
                    content,
                    formatTime(message.getCreatedAt()),
                    isMe,
                    file,
                    message.isDeleted(),
                    message.getDeletedForUsers() != null
                            && message.getDeletedForUsers()
                            .contains(currentUserIdSupplier.get()));
            messages.add(item);
            if (file && message.getFileName() != null) {
                sentFiles.add(message.getFileName());
            } else if (content != null) {
                Matcher matcher = LINK_PATTERN.matcher(content);
                while (matcher.find()) sentLinks.add(matcher.group());
            }
            conversations.applyPinnedRealtime(message, item);
        });
    }

    private boolean isCurrent(MessageResponse message) {
        ConversationResponse current = activeConversation.get();
        return current != null && message.getConversationId().equals(current.getId());
    }

    private void incrementConversationVersion() {
        conversationVersion.set(conversationVersion.get() + 1);
    }

    private String displayTyping(String username) {
        return username == null || username.isBlank()
                ? "Đang nhập..." : username + " đang nhập...";
    }

    private String formatTime(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            return value.contains("T")
                    ? value.substring(value.indexOf("T") + 1, value.indexOf("T") + 6)
                    : value;
        } catch (Exception ignored) {
            return "";
        }
    }

    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() != null
                ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
