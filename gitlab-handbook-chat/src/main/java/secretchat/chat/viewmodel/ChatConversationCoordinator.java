package secretchat.chat.viewmodel;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import secretchat.chat.service.ChatService;
import secretchat.dto.request.CreatePersonalConversationRequest;
import secretchat.dto.request.MessageStatusRequest;
import secretchat.dto.response.ConversationResponse;
import secretchat.dto.response.GroupMemberResponse;
import secretchat.dto.response.GroupResponse;
import secretchat.dto.response.MessageResponse;
import secretchat.dto.response.UserResponse;
import secretchat.util.IdUtils;
import secretchat.util.MessageTimeFormatter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Owns conversation selection, history, pinned messages, and conversation-local state.
 */
final class ChatConversationCoordinator {
    private static final System.Logger LOGGER =
            System.getLogger(ChatConversationCoordinator.class.getName());
    private static final Pattern LINK_PATTERN = Pattern.compile("(https?://\\S+|www\\.\\S+)");

    private final ChatService chatService;
    private final Supplier<String> tokenSupplier;
    private final Supplier<String> currentUserIdSupplier;
    private final Function<String, String> displayNameResolver;
    private final Consumer<String> conversationSubscriber;
    private final Consumer<String> errorConsumer;
    private final Map<String, UserResponse> usersByName;
    private final Map<String, String> userIdsByName;
    private final Map<String, GroupResponse> groupsByName;
    private final Map<String, ConversationResponse> personalConversations;
    private final Map<String, ConversationResponse> groupConversations;
    private final ObservableList<ChatViewModel.MessageItem> messages;
    private final ObservableList<String> members;
    private final ObservableList<String> sentFiles;
    private final ObservableList<String> sentLinks;
    private final ObservableList<ChatViewModel.PinnedMessageItem> pinnedMessages;
    private final StringProperty currentChatName;
    private final BooleanProperty currentChatIsGroup;
    private final ObjectProperty<ConversationResponse> activeConversation;
    private final StringProperty typingText;
    private final IntegerProperty conversationVersion;
    private final Set<String> displayedMessageIds = ConcurrentHashMap.newKeySet();
    private final AtomicLong messageLoadVersion = new AtomicLong();
    private final AtomicLong pinnedLoadVersion = new AtomicLong();

    ChatConversationCoordinator(
            ChatService chatService,
            Supplier<String> tokenSupplier,
            Supplier<String> currentUserIdSupplier,
            Function<String, String> displayNameResolver,
            Consumer<String> conversationSubscriber,
            Consumer<String> errorConsumer,
            Map<String, UserResponse> usersByName,
            Map<String, String> userIdsByName,
            Map<String, GroupResponse> groupsByName,
            Map<String, ConversationResponse> personalConversations,
            Map<String, ConversationResponse> groupConversations,
            ObservableList<ChatViewModel.MessageItem> messages,
            ObservableList<String> members,
            ObservableList<String> sentFiles,
            ObservableList<String> sentLinks,
            ObservableList<ChatViewModel.PinnedMessageItem> pinnedMessages,
            StringProperty currentChatName,
            BooleanProperty currentChatIsGroup,
            ObjectProperty<ConversationResponse> activeConversation,
            StringProperty typingText,
            IntegerProperty conversationVersion) {
        this.chatService = chatService;
        this.tokenSupplier = tokenSupplier;
        this.currentUserIdSupplier = currentUserIdSupplier;
        this.displayNameResolver = displayNameResolver;
        this.conversationSubscriber = conversationSubscriber;
        this.errorConsumer = errorConsumer;
        this.usersByName = usersByName;
        this.userIdsByName = userIdsByName;
        this.groupsByName = groupsByName;
        this.personalConversations = personalConversations;
        this.groupConversations = groupConversations;
        this.messages = messages;
        this.members = members;
        this.sentFiles = sentFiles;
        this.sentLinks = sentLinks;
        this.pinnedMessages = pinnedMessages;
        this.currentChatName = currentChatName;
        this.currentChatIsGroup = currentChatIsGroup;
        this.activeConversation = activeConversation;
        this.typingText = typingText;
        this.conversationVersion = conversationVersion;
    }

    void selectPrivate(String selectedName) {
        UserResponse selectedUser = usersByName.get(selectedName);
        if (selectedUser == null) return;
        beginSwitch(selectedName, false);

        String targetUserId = userIdsByName.get(selectedName);
        if (targetUserId == null && selectedUser.getKeycloakUserId() != null) {
            targetUserId = selectedUser.getId();
        }
        if (targetUserId == null || targetUserId.isBlank()) {
            errorConsumer.accept("Không thể xác định người nhận cuộc trò chuyện.");
            return;
        }
        if (currentUserIdSupplier.get().equals(targetUserId)) {
            errorConsumer.accept("Không thể mở cuộc trò chuyện với chính bạn.");
            return;
        }

        ConversationResponse conversation = personalConversations.get(targetUserId);
        if (conversation == null) {
            try {
                CreatePersonalConversationRequest request =
                        new CreatePersonalConversationRequest();
                request.setSenderId(currentUserIdSupplier.get());
                request.setReceiverId(targetUserId);
                conversation = chatService.createPersonalConversation(
                        request, tokenSupplier.get());
                if (conversation != null && conversation.getId() != null) {
                    personalConversations.put(targetUserId, conversation);
                }
            } catch (Exception error) {
                errorConsumer.accept("Không thể mở cuộc trò chuyện: " + error.getMessage());
                return;
            }
        }
        activate(conversation, selectedName, null);
    }

    void selectGroup(String selectedName) {
        GroupResponse group = groupsByName.get(selectedName);
        if (group == null) return;
        beginSwitch(selectedName, true);

        Long groupId = IdUtils.parseLongId(group.getId());
        if (groupId == null) {
            errorConsumer.accept("ID nhóm không hợp lệ.");
            return;
        }
        ConversationResponse conversation = groupConversations.get(group.getId());
        if (conversation == null) {
            try {
                conversation = chatService.createGroupConversation(
                        groupId, tokenSupplier.get());
                if (conversation != null && conversation.getId() != null) {
                    groupConversations.put(group.getId(), conversation);
                }
            } catch (Exception error) {
                errorConsumer.accept(
                        "Không thể mở cuộc trò chuyện nhóm: " + error.getMessage());
                return;
            }
        }
        activate(conversation, selectedName, group);
    }

    void loadGroupInfo(GroupResponse group) {
        Platform.runLater(() -> {
            members.clear();
            sentFiles.clear();
            sentLinks.clear();
            pinnedMessages.clear();
        });
        CompletableFuture.runAsync(() -> {
            try {
                GroupMemberResponse[] groupMembers = chatService.getGroupMembersList(
                        IdUtils.parseLongId(group.getId()), tokenSupplier.get());
                if (groupMembers == null) return;
                Platform.runLater(() -> {
                    ConversationResponse current = activeConversation.get();
                    if (current == null || !group.getId().equals(current.getGroupId())) return;
                    for (GroupMemberResponse member : groupMembers) {
                        String name = displayNameResolver.apply(member.getUserId());
                        if ("OWNER".equals(member.getRole())) name += " (Chủ nhóm)";
                        else if ("ADMIN".equals(member.getRole())) name += " (Phó nhóm)";
                        members.add(name);
                    }
                });
            } catch (Exception error) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Không thể tải danh sách thành viên nhóm: " + group.getId(), error);
            }
        });
    }

    ChatViewModel.MessageItem toMessageItem(MessageResponse message) {
        String currentUserId = currentUserIdSupplier.get();
        boolean isMe = message.getSenderId() != null
                && message.getSenderId().equals(currentUserId);
        boolean isFile = !"TEXT".equalsIgnoreCase(message.getMessageType());
        String content = isFile && message.getFileName() != null
                ? message.getFileName() : message.getContent();
        return new ChatViewModel.MessageItem(
                message,
                displayNameResolver.apply(message.getSenderId()),
                content,
                MessageTimeFormatter.format(message.getCreatedAt()),
                isMe,
                isFile,
                message.isDeleted(),
                message.getDeletedForUsers() != null
                        && message.getDeletedForUsers().contains(currentUserId));
    }

    ChatViewModel.MessageItem findMessage(String messageId) {
        if (messageId == null) return null;
        for (ChatViewModel.MessageItem item : messages) {
            if (item.getResponse() != null
                    && messageId.equals(item.getResponse().getId())) return item;
        }
        return null;
    }

    ChatViewModel.MessageItem findMatchingPending(MessageResponse message) {
        boolean incomingFile = !"TEXT".equalsIgnoreCase(message.getMessageType());
        String incomingContent = incomingFile ? message.getFileName() : message.getContent();
        for (ChatViewModel.MessageItem item : messages) {
            if (!item.isMe() || item.getResponse() == null
                    || item.getResponse().getId() == null
                    || !item.getResponse().getId().startsWith("pending-")
                    || item.isFile() != incomingFile) continue;
            if (java.util.Objects.equals(item.getContent(), incomingContent)) return item;
        }
        return null;
    }

    ConversationResponse findConversation(String conversationId) {
        for (ConversationResponse value : personalConversations.values()) {
            if (conversationId.equals(value.getId())) return value;
        }
        for (ConversationResponse value : groupConversations.values()) {
            if (conversationId.equals(value.getId())) return value;
        }
        return null;
    }

    boolean registerDisplayedMessage(String messageId) {
        return messageId == null || displayedMessageIds.add(messageId);
    }

    void updateMessageStatus(MessageResponse message, String status) {
        if (message.getId() == null || status.equals(message.getStatus())) return;
        CompletableFuture.runAsync(() -> {
            try {
                MessageStatusRequest request = new MessageStatusRequest();
                request.setUserId(currentUserIdSupplier.get());
                request.setStatus(status);
                chatService.updateMessageStatus(
                        IdUtils.parseLongId(message.getId()), request, tokenSupplier.get());
            } catch (Exception error) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Không thể cập nhật trạng thái tin nhắn", error);
            }
        });
    }

    void applyPinnedRealtime(MessageResponse response, ChatViewModel.MessageItem item) {
        ConversationResponse current = activeConversation.get();
        if (current == null || response == null
                || !current.getId().equals(response.getConversationId())
                || response.getId() == null) return;
        boolean currentlyPinned = pinnedMessages.stream()
                .anyMatch(pinned -> response.getId().equals(pinned.messageId()));
        if (currentlyPinned == response.isPinned()) return;
        ChatViewModel.MessageItem resolved = item != null ? item : toMessageItem(response);
        resolved.setPinned(response.isPinned());
        applyPinnedState(resolved);
        loadPinnedMessages(current.getId(), false);
    }

    void applyPinnedState(ChatViewModel.MessageItem item) {
        if (item == null || item.getResponse() == null
                || item.getResponse().getId() == null) return;
        String messageId = item.getResponse().getId();
        pinnedMessages.removeIf(pinned -> messageId.equals(pinned.messageId()));
        if (item.isPinned() && !item.isDeleted() && !item.isDeletedForMe()) {
            pinnedMessages.add(new ChatViewModel.PinnedMessageItem(item));
        }
    }

    ChatViewModel.MessageItem search(String query) {
        if (activeConversation.get() == null || query == null || query.isBlank()) return null;
        String normalized = query.trim().toLowerCase();
        return messages.stream()
                .filter(item -> !item.isDeleted() && !item.isDeletedForMe())
                .filter(item -> item.getContent() != null
                        && item.getContent().toLowerCase().contains(normalized))
                .findFirst()
                .orElse(null);
    }

    CompletableFuture<ChatViewModel.MessageItem> ensureLoaded(String messageId) {
        ChatViewModel.MessageItem existing = findMessage(messageId);
        if (existing != null) return CompletableFuture.completedFuture(existing);
        ConversationResponse conversation = activeConversation.get();
        if (conversation == null || messageId == null) {
            return CompletableFuture.completedFuture(null);
        }
        String conversationId = conversation.getId();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return chatService.getMessagesAround(
                        IdUtils.parseLongId(messageId), 20, tokenSupplier.get());
            } catch (Exception error) {
                throw new java.util.concurrent.CompletionException(error);
            }
        }).thenCompose(responses -> mergeAroundMessages(
                conversationId, messageId, responses))
                .exceptionally(error -> {
                    Platform.runLater(() -> errorConsumer.accept(
                            "Không thể tải tin nhắn gốc: " + rootMessage(error)));
                    return null;
                });
    }

    void clear() {
        messageLoadVersion.incrementAndGet();
        pinnedLoadVersion.incrementAndGet();
        Runnable clear = () -> {
            messages.clear();
            members.clear();
            sentFiles.clear();
            sentLinks.clear();
            pinnedMessages.clear();
            currentChatName.set(null);
            currentChatIsGroup.set(false);
            activeConversation.set(null);
        };
        if (Platform.isFxApplicationThread()) clear.run();
        else Platform.runLater(clear);
    }

    private void activate(
            ConversationResponse conversation, String chatName, GroupResponse group) {
        activeConversation.set(conversation);
        if (conversation == null || conversation.getId() == null) {
            errorConsumer.accept(group == null
                    ? "Không thể mở cuộc trò chuyện. Vui lòng thử lại."
                    : "Không thể mở cuộc trò chuyện nhóm. Vui lòng thử lại.");
            return;
        }
        markReadAsync(conversation);
        conversation.setUnreadCount(0);
        conversationVersion.set(conversationVersion.get() + 1);
        if (group == null) loadPersonalInfo(chatName);
        else loadGroupInfo(group);
        loadMessages(conversation.getId());
        loadPinnedMessages(conversation.getId(), true);
        conversationSubscriber.accept(conversation.getId());
    }

    private void beginSwitch(String chatName, boolean group) {
        messageLoadVersion.incrementAndGet();
        pinnedLoadVersion.incrementAndGet();
        activeConversation.set(null);
        currentChatName.set(chatName);
        currentChatIsGroup.set(group);
        typingText.set(null);
        displayedMessageIds.clear();
        messages.clear();
        members.clear();
        sentFiles.clear();
        sentLinks.clear();
        pinnedMessages.clear();
    }

    private void loadPersonalInfo(String chatName) {
        Platform.runLater(() -> {
            members.clear();
            sentFiles.clear();
            sentLinks.clear();
            pinnedMessages.clear();
            members.add("Bạn");
            members.add(chatName);
        });
    }

    private void markReadAsync(ConversationResponse conversation) {
        if (conversation.getUnreadCount() <= 0) return;
        CompletableFuture.runAsync(() -> {
            try {
                chatService.markConversationAsRead(
                        IdUtils.parseLongId(conversation.getId()), tokenSupplier.get());
            } catch (Exception error) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Không thể đánh dấu cuộc trò chuyện đã đọc: "
                                + conversation.getId(), error);
            }
        });
    }

    private void loadMessages(String conversationId) {
        long version = messageLoadVersion.incrementAndGet();
        displayedMessageIds.clear();
        messages.clear();
        CompletableFuture.runAsync(() -> {
            try {
                MessageResponse[] history = chatService.getConversationChatHistory(
                        IdUtils.parseLongId(conversationId), tokenSupplier.get());
                HistorySnapshot snapshot = buildHistory(history);
                Platform.runLater(() -> applyHistory(conversationId, version, snapshot));
            } catch (Exception error) {
                LOGGER.log(System.Logger.Level.ERROR, "Lỗi khi tải lịch sử tin nhắn", error);
                Platform.runLater(() -> {
                    ConversationResponse current = activeConversation.get();
                    if (version == messageLoadVersion.get() && current != null
                            && conversationId.equals(current.getId())) {
                        errorConsumer.accept(
                                "Không thể tải lịch sử tin nhắn: " + error.getMessage());
                    }
                });
            }
        });
    }

    private HistorySnapshot buildHistory(MessageResponse[] history) {
        List<ChatViewModel.MessageItem> items = new ArrayList<>();
        List<String> files = new ArrayList<>();
        List<String> links = new ArrayList<>();
        Set<String> ids = ConcurrentHashMap.newKeySet();
        if (history == null) return new HistorySnapshot(items, files, links);

        String currentUserId = currentUserIdSupplier.get();
        for (MessageResponse message : history) {
            if (message.getId() != null && !ids.add(message.getId())) continue;
            boolean isMe = currentUserId.equals(message.getSenderId());
            if (!isMe && !"SEEN".equals(message.getStatus())) {
                updateMessageStatus(message, "SEEN");
            }
            ChatViewModel.MessageItem item = toMessageItem(message);
            items.add(item);
            if (item.isFile() && !item.isDeleted() && !item.isDeletedForMe()) {
                files.add(message.getFileName() == null ? "file" : message.getFileName());
            } else if (!item.isDeleted() && !item.isDeletedForMe()
                    && message.getContent() != null) {
                Matcher matcher = LINK_PATTERN.matcher(message.getContent());
                while (matcher.find()) links.add(matcher.group());
            }
        }
        return new HistorySnapshot(items, files, links);
    }

    private void applyHistory(
            String conversationId, long version, HistorySnapshot snapshot) {
        ConversationResponse current = activeConversation.get();
        if (version != messageLoadVersion.get() || current == null
                || !conversationId.equals(current.getId())) return;
        LinkedHashMap<String, ChatViewModel.MessageItem> merged = new LinkedHashMap<>();
        int transientIndex = 0;
        for (ChatViewModel.MessageItem item : snapshot.messages()) {
            String id = item.getResponse() == null ? null : item.getResponse().getId();
            merged.put(id == null ? "history-" + transientIndex++ : id, item);
        }
        for (ChatViewModel.MessageItem item : messages) {
            String id = item.getResponse() == null ? null : item.getResponse().getId();
            merged.put(id == null ? "current-" + transientIndex++ : id, item);
        }
        messages.setAll(merged.values());
        displayedMessageIds.clear();
        for (ChatViewModel.MessageItem item : messages) {
            if (item.getResponse() != null && item.getResponse().getId() != null) {
                displayedMessageIds.add(item.getResponse().getId());
            }
        }
        if (messages.isEmpty() && "TRỢ LÝ AI".equals(currentChatName.get())) {
            messages.add(new ChatViewModel.MessageItem(
                    null, "TRỢ LÝ AI",
                    "Xin chào! Tôi là Trợ lý AI. Tôi có thể giúp gì cho bạn hôm nay?",
                    java.time.LocalTime.now().format(
                            java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                    false, false, false, false));
        }
        sentFiles.setAll(snapshot.files());
        sentLinks.setAll(snapshot.links());
    }

    private void loadPinnedMessages(String conversationId, boolean clearFirst) {
        long version = pinnedLoadVersion.incrementAndGet();
        if (clearFirst) Platform.runLater(pinnedMessages::clear);
        if (conversationId == null) return;
        CompletableFuture.runAsync(() -> {
            try {
                MessageResponse[] responses = chatService.getPinnedMessages(
                        IdUtils.parseLongId(conversationId), tokenSupplier.get());
                LinkedHashMap<String, ChatViewModel.PinnedMessageItem> unique =
                        new LinkedHashMap<>();
                if (responses != null) {
                    for (MessageResponse response : responses) {
                        if (!isVisiblePinned(conversationId, response)) continue;
                        ChatViewModel.MessageItem item = toMessageItem(response);
                        item.setPinned(true);
                        unique.put(response.getId(),
                                new ChatViewModel.PinnedMessageItem(item));
                    }
                }
                Platform.runLater(() -> {
                    ConversationResponse current = activeConversation.get();
                    if (version == pinnedLoadVersion.get() && current != null
                            && conversationId.equals(current.getId())) {
                        pinnedMessages.setAll(unique.values());
                    }
                });
            } catch (Exception error) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Không thể tải danh sách tin nhắn ghim", error);
                Platform.runLater(() -> {
                    if (version == pinnedLoadVersion.get()) pinnedMessages.clear();
                });
            }
        });
    }

    private boolean isVisiblePinned(String conversationId, MessageResponse response) {
        return conversationId.equals(response.getConversationId())
                && response.getId() != null
                && response.isPinned()
                && !response.isDeleted()
                && (response.getDeletedForUsers() == null
                || !response.getDeletedForUsers().contains(currentUserIdSupplier.get()));
    }

    private CompletableFuture<ChatViewModel.MessageItem> mergeAroundMessages(
            String conversationId, String messageId, MessageResponse[] responses) {
        CompletableFuture<ChatViewModel.MessageItem> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            ConversationResponse current = activeConversation.get();
            if (current == null || !conversationId.equals(current.getId())) {
                result.complete(null);
                return;
            }
            LinkedHashMap<String, ChatViewModel.MessageItem> merged = new LinkedHashMap<>();
            int transientIndex = 0;
            for (ChatViewModel.MessageItem item : messages) {
                String id = item.getResponse() == null ? null : item.getResponse().getId();
                merged.put(id == null ? "transient-" + transientIndex++ : id, item);
            }
            if (responses != null) {
                for (MessageResponse response : responses) {
                    if (conversationId.equals(response.getConversationId())
                            && response.getId() != null) {
                        merged.putIfAbsent(response.getId(), toMessageItem(response));
                    }
                }
            }
            List<ChatViewModel.MessageItem> ordered = new ArrayList<>(merged.values());
            ordered.sort(Comparator.comparing(
                    value -> value.getResponse() == null
                            ? null : value.getResponse().getCreatedAt(),
                    Comparator.nullsLast(String::compareTo)));
            messages.setAll(ordered);
            result.complete(findMessage(messageId));
        });
        return result;
    }

    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() != null
                ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    private record HistorySnapshot(
            List<ChatViewModel.MessageItem> messages,
            List<String> files,
            List<String> links) {}
}
