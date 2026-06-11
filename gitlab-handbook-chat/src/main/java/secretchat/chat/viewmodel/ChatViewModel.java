package secretchat.chat.viewmodel;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import secretchat.chat.service.AIService;
import secretchat.chat.service.ChatService;
import secretchat.chat.service.PresenceHeartbeatService;
import secretchat.chat.service.RealtimeChatService;
import secretchat.common.exception.ApiException;
import secretchat.dto.request.*;
import secretchat.dto.response.*;
import secretchat.service.ApiClient;
import secretchat.service.SessionManager;
import secretchat.util.IdUtils;
import secretchat.util.TokenUtils;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatViewModel {

    private static final System.Logger LOGGER = System.getLogger(ChatViewModel.class.getName());

    private final ChatService chatService;
    private final AIService aiService;
    private final RealtimeChatService realtimeChatService;
    private final ChatProfileCoordinator profileCoordinator;
    private final ChatGroupCoordinator groupCoordinator;
    private final ChatDirectoryCoordinator directoryCoordinator;
    private final ChatMessageActionCoordinator messageActionCoordinator;
    private final ChatMessageSender messageSender;
    private final ChatConversationCoordinator conversationCoordinator;
    private final ChatRealtimeCoordinator realtimeCoordinator;
    private final PresenceHeartbeatService presenceHeartbeatService;
    
    private String token;
    private String currentUserId;
    private UserResponse currentUserResponse;
    private volatile boolean applicationActive = true;

    private final Map<String, UserResponse> nameToUserMap;
    private final Map<String, String> displayNameToUserId;
    private final Map<String, GroupResponse> nameToGroupMap;
    private final Map<String, ConversationResponse> groupConversationMap;
    private final Map<String, ConversationResponse> personalConversationMap;

    private static final Pattern LINK_PATTERN = Pattern.compile("(https?://\\S+|www\\.\\S+)");

    // Observable states
    private final ObservableList<String> privateChatList;
    private final ObservableList<String> groupChatList;
    private final ObservableList<MessageItem> messages = FXCollections.observableArrayList();
    
    private final ObservableList<String> memberList = FXCollections.observableArrayList();
    private final ObservableList<String> sentFileList = FXCollections.observableArrayList();
    private final ObservableList<String> sentLinkList = FXCollections.observableArrayList();
    private final ObservableList<PinnedMessageItem> pinnedMessageList = FXCollections.observableArrayList();

    private final StringProperty currentChatName = new SimpleStringProperty();
    private final BooleanProperty currentChatIsGroup = new SimpleBooleanProperty(false);
    private final ObjectProperty<ConversationResponse> activeConversation = new SimpleObjectProperty<>();

    private final BooleanProperty isLoading = new SimpleBooleanProperty(false);
    private final StringProperty errorMessage = new SimpleStringProperty();
    private final StringProperty notificationMessage = new SimpleStringProperty();
    private final StringProperty typingText = new SimpleStringProperty();
    private final BooleanProperty aiLoading = new SimpleBooleanProperty(false);
    private final BooleanProperty sessionExpired = new SimpleBooleanProperty(false);
    private final IntegerProperty conversationVersion = new SimpleIntegerProperty();
    private final ObjectProperty<NewMessageEvent> newMessageEvent = new SimpleObjectProperty<>();
    public ChatViewModel() {
        this.chatService = new ChatService(ApiClient.getInstance());
        this.aiService = new AIService();
        this.realtimeChatService = new RealtimeChatService();
        this.presenceHeartbeatService = new PresenceHeartbeatService(chatService);
        this.directoryCoordinator = new ChatDirectoryCoordinator(
                chatService,
                () -> token,
                () -> currentUserId,
                errorMessage::set,
                notificationMessage::set,
                this::clearRemovedFriendSelection);
        this.nameToUserMap = directoryCoordinator.usersByName();
        this.displayNameToUserId = directoryCoordinator.userIdsByDisplayName();
        this.nameToGroupMap = directoryCoordinator.groupsByName();
        this.groupConversationMap = directoryCoordinator.groupConversations();
        this.personalConversationMap = directoryCoordinator.personalConversations();
        this.privateChatList = directoryCoordinator.privateChats();
        this.groupChatList = directoryCoordinator.groupChats();
        this.conversationCoordinator = new ChatConversationCoordinator(
                chatService,
                () -> token,
                () -> currentUserId,
                this::getUserDisplayName,
                this::subscribeToConversation,
                errorMessage::set,
                nameToUserMap,
                displayNameToUserId,
                nameToGroupMap,
                personalConversationMap,
                groupConversationMap,
                messages,
                memberList,
                sentFileList,
                sentLinkList,
                pinnedMessageList,
                currentChatName,
                currentChatIsGroup,
                activeConversation,
                typingText,
                conversationVersion);
        this.messageActionCoordinator = new ChatMessageActionCoordinator(
                chatService,
                () -> token,
                () -> currentUserId,
                errorMessage::set,
                conversationCoordinator::applyPinnedState,
                conversationCoordinator::findMessage);
        this.messageSender = new ChatMessageSender(
                chatService,
                aiService,
                realtimeChatService,
                () -> token,
                () -> currentUserId,
                () -> currentUserResponse,
                activeConversation::get,
                currentChatName::get,
                messages,
                errorMessage::set,
                aiLoading::set);
        this.realtimeCoordinator = new ChatRealtimeCoordinator(
                chatService,
                realtimeChatService,
                directoryCoordinator,
                conversationCoordinator,
                () -> token,
                () -> currentUserId,
                () -> currentUserResponse,
                () -> applicationActive,
                errorMessage::set,
                this::createNewMessageEvent,
                messages,
                sentFileList,
                sentLinkList,
                activeConversation,
                typingText,
                conversationVersion,
                newMessageEvent);
        this.profileCoordinator = new ChatProfileCoordinator(
                chatService,
                () -> token,
                () -> currentUserResponse,
                profile -> currentUserResponse = profile);
        ChatGroupHostAdapter groupHost = new ChatGroupHostAdapter(
                () -> token,
                () -> currentUserId,
                nameToUserMap,
                nameToGroupMap,
                groupConversationMap,
                groupChatList,
                messages,
                memberList,
                sentFileList,
                sentLinkList,
                pinnedMessageList,
                currentChatName,
                currentChatIsGroup,
                activeConversation,
                this::getUserDisplayName,
                conversationCoordinator::loadGroupInfo,
                errorMessage::set,
                notificationMessage::set);
        this.groupCoordinator = new ChatGroupCoordinator(chatService, groupHost);
    }

    public void init() {
        try {
            token = ApiClient.getInstance().refreshSession();
        } catch (Exception error) {
            SessionManager.getInstance().clear();
            sessionExpired.set(true);
            return;
        }

        realtimeChatService.connect(token)
                .exceptionally(error -> {
                    Platform.runLater(() -> errorMessage.set(
                            "Không thể kết nối WebSocket qua gateway: " + rootMessage(error)));
                    return null;
                });

        try {
            isLoading.set(true);
            String username = TokenUtils.getUsernameFromToken(token);
            if (username == null) {
                errorMessage.set("Token không hợp lệ.");
                return;
            }

            currentUserResponse = TokenUtils.getCurrentUserFromToken(token);
            if (currentUserResponse == null || currentUserResponse.getKeycloakUserId() == null) {
                errorMessage.set("Không thể xác thực thông tin người dùng từ token.");
                return;
            }

            try {
                var profile = chatService.getUserProfileByExternalSub(currentUserResponse.getKeycloakUserId(), token);
                if (profile != null && profile.getId() != null && !profile.getId().isBlank()) {
                    currentUserId = profile.getId();
                } else {
                    currentUserId = resolveCurrentUserId(username, currentUserResponse.getKeycloakUserId());
                }
            } catch (Exception e) {
                if (e instanceof ApiException apiEx && apiEx.getStatusCode() == 404) {
                    currentUserId = resolveCurrentUserId(username, currentUserResponse.getKeycloakUserId());
                } else {
                    currentUserId = currentUserResponse.getKeycloakUserId();
                }
            }

            if (currentUserId == null || currentUserId.isBlank()) {
                errorMessage.set("ID người dùng không hợp lệ.");
                return;
            }

            subscribeToRealtimeUpdates();
            presenceHeartbeatService.start();
            loadData();

        } catch (Exception e) {
            errorMessage.set("Không thể kết nối tới máy chủ: " + e.getMessage());
        } finally {
            isLoading.set(false);
        }
    }

    private String resolveCurrentUserId(String username, String externalSub) {
        try {
            UserResponse user = chatService.getUserByUsername(username, token);
            if (user != null && user.getId() != null && !user.getId().isBlank()) {
                return user.getId();
            }
        } catch (Exception ex) {
            // Log ignored
        }
        return externalSub;
    }

    public void loadData() {
        directoryCoordinator.loadData();
    }

    public String getUserDisplayName(String userId) {
        return directoryCoordinator.getUserDisplayName(userId);
    }

    public void selectPrivateChat(String selectedUserStr) {
        conversationCoordinator.selectPrivate(selectedUserStr);
    }

    public void selectGroupChat(String selectedGroupStr) {
        conversationCoordinator.selectGroup(selectedGroupStr);
    }

    public void sendMessage(String text, File file) {
        messageSender.send(text, file);
    }

    private void subscribeToRealtimeUpdates() {
        realtimeCoordinator.subscribeUserUpdates();
    }

    public void close() {
        presenceHeartbeatService.close();
        realtimeCoordinator.close();
    }

    private void subscribeToConversation(String conversationId) {
        realtimeCoordinator.subscribeConversation(conversationId);
    }

    public void sendTyping(boolean typing) {
        realtimeCoordinator.sendTyping(typing);
    }

    public void reactToMessage(MessageItem item, String emoji) {
        realtimeCoordinator.react(item, emoji);
    }

    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    public void createGroup(String groupName, String groupDesc) {
        groupCoordinator.createGroup(groupName, groupDesc);
    }

    public void addFriend(String username) {
        directoryCoordinator.addFriend(username);
    }

    public CompletableFuture<java.util.List<FriendResponse>> loadIncomingFriendRequests() {
        return directoryCoordinator.loadIncomingFriendRequests();
    }

    public CompletableFuture<Void> acceptFriendRequest(FriendResponse request) {
        return directoryCoordinator.acceptFriendRequest(request);
    }

    public CompletableFuture<Void> rejectFriendRequest(FriendResponse request) {
        return directoryCoordinator.rejectFriendRequest(request);
    }

    public void addGroupMember(String selectedUserStr) {
        groupCoordinator.addMember(selectedUserStr);
    }

    public void leaveGroup() {
        groupCoordinator.leaveGroup();
    }

    public void kickMember(String memberName) {
        groupCoordinator.kickMember(memberName);
    }

    public void updateRole(String memberName, String newRole) {
        groupCoordinator.updateRole(memberName, newRole);
    }

    public void deleteMessageForUser(MessageResponse msg, MessageItem item) {
        messageActionCoordinator.deleteForUser(msg, item);
    }

    public void recallMessage(MessageResponse msg, MessageItem item) {
        messageActionCoordinator.recall(msg, item);
    }

    public void removeFriend(String displayName) {
        directoryCoordinator.removeFriend(displayName);
    }

    public java.util.List<GroupMemberView> getCurrentGroupMembers() {
        return groupCoordinator.getCurrentMembers();
    }

    public java.util.List<String> getAvailableGroupMemberNames() {
        return groupCoordinator.getAvailableMemberNames();
    }

    public void removeGroupMemberById(String userId) {
        groupCoordinator.removeMemberById(userId);
    }

    public void transferGroupOwnership(String newOwnerId) {
        groupCoordinator.transferOwnership(newOwnerId);
    }

    public void deleteCurrentGroup() {
        groupCoordinator.deleteCurrentGroup();
    }

    public void editMessage(MessageItem item, String content) {
        messageActionCoordinator.edit(item, content);
    }

    public void toggleStar(MessageItem item) {
        messageActionCoordinator.toggleStar(item);
    }

    public void togglePin(MessageItem item) {
        messageActionCoordinator.togglePin(item);
    }

    public void unpinMessage(PinnedMessageItem pinned) {
        messageActionCoordinator.unpin(pinned);
    }

    public MessageItem searchConversationMessage(String query) {
        return conversationCoordinator.search(query);
    }

    public void openPrivateChatForMember(String displayedName) {
        String memberName = displayedName;
        if (memberName == null || "Bạn".equals(memberName)) return;
        memberName = memberName.replace(" (Chủ nhóm)", "").replace(" (Phó nhóm)", "");
        if (!nameToUserMap.containsKey(memberName)) {
            String userId = displayNameToUserId.get(memberName);
            if (userId == null) return;
        }
        selectPrivateChat(memberName);
    }

    public void clearConversationData() {
        conversationCoordinator.clear();
    }

    public byte[] downloadFile(MessageResponse msg) throws Exception {
        return chatService.downloadMessageFile(IdUtils.parseLongId(msg.getId()), token);
    }

    public CompletableFuture<MessageItem> ensureMessageLoaded(String messageId) {
        return conversationCoordinator.ensureLoaded(messageId);
    }

    public void performSearch(String keyword) {
        Platform.runLater(() -> directoryCoordinator.performSearch(keyword));
    }

    // Getters for Observables and Properties
    public ObservableList<String> getPrivateChatList() { return privateChatList; }
    public ObservableList<String> getGroupChatList() { return groupChatList; }
    public ObservableList<MessageItem> getMessages() { return messages; }
    public ObservableList<String> getMemberList() { return memberList; }
    public ObservableList<String> getSentFileList() { return sentFileList; }
    public ObservableList<String> getSentLinkList() { return sentLinkList; }
    public ObservableList<PinnedMessageItem> getPinnedMessageList() { return pinnedMessageList; }

    public StringProperty currentChatNameProperty() { return currentChatName; }
    public BooleanProperty currentChatIsGroupProperty() { return currentChatIsGroup; }
    public ObjectProperty<ConversationResponse> activeConversationProperty() { return activeConversation; }
    public BooleanProperty isLoadingProperty() { return isLoading; }
    public StringProperty errorMessageProperty() { return errorMessage; }
    public StringProperty notificationMessageProperty() { return notificationMessage; }
    public StringProperty typingTextProperty() { return typingText; }
    public BooleanProperty aiLoadingProperty() { return aiLoading; }
    public BooleanProperty sessionExpiredProperty() { return sessionExpired; }
    public IntegerProperty conversationVersionProperty() { return conversationVersion; }
    public ObjectProperty<NewMessageEvent> newMessageEventProperty() { return newMessageEvent; }

    public String getCurrentUserId() { return currentUserId; }
    public String getToken() { return token; }

    public CompletableFuture<UserResponse> loadCurrentUserProfile() {
        return profileCoordinator.loadCurrentUserProfile();
    }

    public UserResponse getCurrentUserProfileSnapshot() {
        return currentUserResponse;
    }

    public CompletableFuture<UserResponse> loadGroupMemberProfile(String userId) {
        return profileCoordinator.loadGroupMemberProfile(userId);
    }

    public void openPrivateChatForProfile(UserResponse profile) {
        if (profile == null || profile.getKeycloakUserId() == null
                || profile.getKeycloakUserId().equals(
                        currentUserResponse == null ? null : currentUserResponse.getKeycloakUserId())) {
            return;
        }
        String displayName = profile.getFullName();
        if (displayName == null || displayName.isBlank()) {
            displayName = profile.getUsername();
        }
        if (displayName == null || displayName.isBlank()) return;
        nameToUserMap.put(displayName, profile);
        displayNameToUserId.put(displayName, profile.getKeycloakUserId());
        if (!privateChatList.contains(displayName)) privateChatList.add(displayName);
        selectPrivateChat(displayName);
    }

    public CompletableFuture<UserResponse> updateCurrentUserProfile(UpdateUserProfileRequest request) {
        return profileCoordinator.updateCurrentUserProfile(request);
    }

    public void setApplicationActive(boolean value) {
        boolean becameActive = value && !applicationActive;
        applicationActive = value;
        if (becameActive) markActiveConversationRead();
    }

    private void markActiveConversationRead() {
        ConversationResponse conversation = activeConversation.get();
        if (conversation == null || conversation.getId() == null || conversation.getUnreadCount() <= 0) return;
        CompletableFuture.runAsync(() -> {
            try {
                chatService.markConversationAsRead(IdUtils.parseLongId(conversation.getId()), token);
                Platform.runLater(() -> {
                    conversation.setUnreadCount(0);
                    conversationVersion.set(conversationVersion.get() + 1);
                });
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Không thể đánh dấu cuộc trò chuyện đã đọc", e);
            }
        });
    }

    public int getUnreadCountForUser(String userId) {
        ConversationResponse c = personalConversationMap.get(userId);
        return c != null ? c.getUnreadCount() : 0;
    }

    public int getUnreadCountForGroup(String groupId) {
        ConversationResponse c = groupConversationMap.get(groupId);
        return c != null ? c.getUnreadCount() : 0;
    }

    public String getUserIdByDisplayName(String displayName) {
        return displayNameToUserId.get(displayName);
    }

    public UserResponse getUserByName(String displayName) {
        return nameToUserMap.get(displayName);
    }
    
    public GroupResponse getGroupByName(String groupName) {
        return nameToGroupMap.get(groupName);
    }

    public boolean isGroupCreator(String groupName) {
        GroupResponse g = nameToGroupMap.get(groupName);
        return g != null && currentUserId.equals(g.getCreatorId());
    }

    private void clearRemovedFriendSelection(String displayName) {
        if (!displayName.equals(currentChatName.get())) return;
        messages.clear();
        currentChatName.set(null);
        activeConversation.set(null);
    }

    private NewMessageEvent createNewMessageEvent(
            MessageResponse message, ConversationResponse conversation) {
        boolean group = "GROUP".equalsIgnoreCase(conversation.getType());
        String chatName;
        if (group) {
            GroupResponse groupResponse = nameToGroupMap.values().stream()
                    .filter(value -> java.util.Objects.equals(value.getId(), conversation.getGroupId()))
                    .findFirst().orElse(null);
            chatName = groupResponse == null ? "Nhóm chat" : groupResponse.getName();
        } else {
            chatName = getUserDisplayName(message.getSenderId());
        }
        String preview = !"TEXT".equalsIgnoreCase(message.getMessageType())
                ? "Đã gửi một file: " + (message.getFileName() == null ? "Tệp đính kèm" : message.getFileName())
                : message.getContent();
        return new NewMessageEvent(conversation.getId(), chatName, group, preview);
    }

    public static class MessageItem extends ChatMessageItem {
        public MessageItem(MessageResponse response, String senderName, String content, String time, boolean isMe, boolean isFile, boolean isDeleted, boolean isDeletedForMe) {
            super(response, senderName, content, time, isMe, isFile, isDeleted, isDeletedForMe);
        }
    }

    public record GroupMemberView(String userId, String displayName, String role) {}
    public record PinnedMessageItem(MessageItem message) {
        public String messageId() { return message.getResponse().getId(); }
        public String content() { return message.getContent(); }
        public String sender() { return message.getSenderName(); }
        public String time() {
            String pinnedAt = message.getResponse().getUpdatedAt();
            return pinnedAt == null || pinnedAt.isBlank()
                    ? message.getTime()
                    : formatDisplayTime(pinnedAt);
        }
        public String type() {
            String type = message.getResponse().getMessageType();
            String normalized = type == null ? "TEXT" : type.toUpperCase();
            if ("TEXT".equals(normalized) && message.getContent() != null
                    && LINK_PATTERN.matcher(message.getContent()).find()) {
                return "LINK";
            }
            return normalized;
        }
        public String preview() {
            String type = type();
            if ("IMAGE".equals(type)) return "[Hình ảnh]";
            if ("FILE".equals(type) || "VIDEO".equals(type)) {
                String name = message.getResponse().getFileName();
                return "[Tệp đính kèm]" + (name == null || name.isBlank() ? "" : " " + name);
            }
            String content = message.getContent() == null ? "" : message.getContent();
            Matcher matcher = LINK_PATTERN.matcher(content);
            if (matcher.find()) return "[Liên kết] " + matcher.group();
            return content;
        }
        private static String formatDisplayTime(String value) {
            if (value == null || value.isBlank()) return "";
            int separator = value.indexOf('T');
            if (separator >= 0 && value.length() >= separator + 6) {
                return value.substring(separator + 1, separator + 6);
            }
            return value;
        }
    }
    public record NewMessageEvent(String conversationId, String chatName, boolean group, String preview) {}
}
