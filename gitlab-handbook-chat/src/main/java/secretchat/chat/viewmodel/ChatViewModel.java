package secretchat.chat.viewmodel;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import secretchat.chat.service.AIService;
import secretchat.chat.service.ChatService;
import secretchat.chat.service.RealtimeChatService;
import secretchat.common.exception.ApiException;
import secretchat.dto.request.*;
import secretchat.dto.response.*;
import secretchat.service.ApiClient;
import secretchat.service.SessionManager;
import secretchat.util.FileUtils;
import secretchat.util.IdUtils;
import secretchat.util.TokenUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatViewModel {

    private static final System.Logger LOGGER = System.getLogger(ChatViewModel.class.getName());

    private final ChatService chatService;
    private final AIService aiService;
    private final RealtimeChatService realtimeChatService;
    private final Set<String> displayedMessageIds = ConcurrentHashMap.newKeySet();
    private final Set<String> receivedRealtimeMessageIds = ConcurrentHashMap.newKeySet();
    
    private String token;
    private String currentUserId;
    private UserResponse currentUserResponse;
    private volatile boolean applicationActive = true;

    // Maps to cache data
    private final Map<String, UserResponse> nameToUserMap = new HashMap<>();
    private final Map<String, String> displayNameToUserId = new HashMap<>();
    private final Map<String, GroupResponse> nameToGroupMap = new HashMap<>();
    private final Map<String, ConversationResponse> groupConversationMap = new HashMap<>();
    private final Map<String, ConversationResponse> personalConversationMap = new HashMap<>();

    private static final Pattern LINK_PATTERN = Pattern.compile("(https?://\\S+|www\\.\\S+)");

    // Observable states
    private final ObservableList<String> privateChatList = FXCollections.observableArrayList();
    private final ObservableList<String> groupChatList = FXCollections.observableArrayList();
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
    private final IntegerProperty conversationVersion = new SimpleIntegerProperty();
    private final ObjectProperty<NewMessageEvent> newMessageEvent = new SimpleObjectProperty<>();
    private final AtomicLong pinnedLoadVersion = new AtomicLong();
    private final AtomicLong messageLoadVersion = new AtomicLong();

    public ChatViewModel() {
        this.chatService = new ChatService(ApiClient.getInstance());
        this.aiService = new AIService();
        this.realtimeChatService = new RealtimeChatService();
    }

    public void init() {
        token = SessionManager.getInstance().getAccessToken();
        if (token == null || token.isBlank()) {
            errorMessage.set("Không tìm thấy token đăng nhập. Vui lòng đăng nhập lại.");
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
        Platform.runLater(() -> {
            privateChatList.clear();
            nameToUserMap.clear();
            displayNameToUserId.clear();
            
            // Setup AI Assistant in maps but don't add to list (it has dedicated button now)
            UserResponse aiUser = new UserResponse();
            aiUser.setId("AI_ASSISTANT");
            aiUser.setUsername("TRỢ LÝ AI");
            aiUser.setFullName("TRỢ LÝ AI");
            nameToUserMap.put("TRỢ LÝ AI", aiUser);
            displayNameToUserId.put("TRỢ LÝ AI", "AI_ASSISTANT");
        });

        CompletableFuture.runAsync(() -> {
            try {
                ConversationResponse[] convs = chatService.getUserConversations(currentUserId, token);
                Platform.runLater(() -> {
                    groupChatList.clear();
                    nameToGroupMap.clear();
                });

                if (convs != null) {
                    for (ConversationResponse c : convs) {
                        if ("PERSONAL".equalsIgnoreCase(c.getType())) {
                            String otherUserId = (c.getSenderId() != null && c.getSenderId().equals(currentUserId)) ? c.getReceiverId() : c.getSenderId();
                            if (otherUserId == null && c.getReceiverId() != null) {
                                otherUserId = c.getReceiverId(); // fallback
                            }
                            
                            if (otherUserId != null && !otherUserId.equals(currentUserId)) {
                                personalConversationMap.put(otherUserId, c);
                            }
                        } else if ("GROUP".equalsIgnoreCase(c.getType()) && c.getGroupId() != null) {
                            try {
                                GroupResponse g = chatService.getGroupDetails(IdUtils.parseLongId(c.getGroupId()), token);
                                if (g != null) {
                                    Platform.runLater(() -> {
                                        groupChatList.add(g.getName());
                                        nameToGroupMap.put(g.getName(), g);
                                        groupConversationMap.put(g.getId(), c);
                                    });
                                }
                            } catch (Exception ge) {
                                // Ignore missing groups
                            }
                        }
                    }
                }
                loadFriends();
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.ERROR, "Lỗi tải danh sách cuộc hội thoại", e);
                Platform.runLater(() -> errorMessage.set("Lỗi tải cuộc hội thoại: " + e.getMessage()));
            }
        });
    }

    private void loadFriends() {
        try {
            FriendResponse[] friends = chatService.getFriends(currentUserId, token);
            if (friends != null) {
                for (FriendResponse friend : friends) {
                    String displayName = friend.getFriendUsername();
                    try {
                        UserResponse u = chatService.getUserById(friend.getFriendId(), token);
                        if (u != null && u.getFullName() != null && !u.getFullName().isBlank()) {
                            displayName = u.getFullName();
                        }
                    } catch (Exception ex) {
                        // fallback
                    }
                    if (displayName == null || displayName.isBlank()) {
                        displayName = "Người dùng " + friend.getFriendId();
                    }
                    final String finalDisplayName = displayName;
                    Platform.runLater(() -> {
                        addFriendToList(friend, finalDisplayName);
                    });
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    private String fetchUserDisplayName(String userId) {
        String displayName = null;
        try {
            UserResponse u = chatService.getUserById(userId, token);
            if (u != null) {
                if (u.getFullName() != null && !u.getFullName().isBlank()) {
                    displayName = u.getFullName();
                } else if (u.getUsername() != null && !u.getUsername().isBlank()) {
                    displayName = u.getUsername();
                }
            }
        } catch (Exception ex) {
            // Ignore
        }
        return displayName != null ? displayName : "Người dùng " + userId;
    }

    public String getUserDisplayName(String userId) {
        if (userId != null && userId.equals(currentUserId)) {
            return "Bạn";
        }
        for (var entry : displayNameToUserId.entrySet()) {
            if (entry.getValue().equals(userId)) {
                return entry.getKey();
            }
        }
        for (UserResponse user : nameToUserMap.values()) {
            if (user.getId() != null && user.getId().equals(userId)) {
                return user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUsername();
            }
        }
        
        String fetched = fetchUserDisplayName(userId);
        UserResponse user = new UserResponse();
        user.setId(userId);
        user.setKeycloakUserId(userId);
        user.setUsername(fetched);
        user.setFullName(fetched);
        Platform.runLater(() -> {
            nameToUserMap.put(fetched, user);
            displayNameToUserId.put(fetched, userId);
        });
        return fetched;
    }

    public void selectPrivateChat(String selectedUserStr) {
        UserResponse selectedUser = nameToUserMap.get(selectedUserStr);
        if (selectedUser == null) return;

        beginConversationSwitch(selectedUserStr, false);

        String targetUserId = displayNameToUserId.get(selectedUserStr);
        if (targetUserId == null && selectedUser.getKeycloakUserId() != null) {
            targetUserId = selectedUser.getId();
        }
        if (targetUserId == null || targetUserId.isBlank()) {
            errorMessage.set("Không thể xác định người nhận cuộc trò chuyện.");
            return;
        }

        if (currentUserId.equals(targetUserId)) {
            errorMessage.set("Không thể mở cuộc trò chuyện với chính bạn.");
            return;
        }

        ConversationResponse conv = personalConversationMap.get(targetUserId);
        if (conv == null) {
            try {
                CreatePersonalConversationRequest req = new CreatePersonalConversationRequest();
                req.setSenderId(currentUserId);
                req.setReceiverId(targetUserId);
                conv = chatService.createPersonalConversation(req, token);
                if (conv != null && conv.getId() != null) {
                    personalConversationMap.put(targetUserId, conv);
                }
            } catch (Exception e) {
                errorMessage.set("Không thể mở cuộc trò chuyện: " + e.getMessage());
                return;
            }
        }

        activeConversation.set(conv);
        if (conv == null || conv.getId() == null) {
            errorMessage.set("Không thể mở cuộc trò chuyện. Vui lòng thử lại.");
            return;
        }

        markConversationReadAsync(conv);
        conv.setUnreadCount(0);
        conversationVersion.set(conversationVersion.get() + 1);
        loadChatInfo(selectedUserStr, false);
        loadMessages(conv.getId(), 0); // Ignore unreadCount for loading as it's just marked 0
        loadPinnedMessages(conv.getId());
        subscribeToConversation(conv.getId());
    }

    public void selectGroupChat(String selectedGroupStr) {
        GroupResponse selectedGroup = nameToGroupMap.get(selectedGroupStr);
        if (selectedGroup == null) return;

        beginConversationSwitch(selectedGroupStr, true);

        Long groupId = IdUtils.parseLongId(selectedGroup.getId());
        if (groupId == null) {
            errorMessage.set("ID nhóm không hợp lệ.");
            return;
        }

        ConversationResponse conv = groupConversationMap.get(selectedGroup.getId());
        if (conv == null) {
            try {
                conv = chatService.createGroupConversation(groupId, token);
                if (conv != null && conv.getId() != null) {
                    groupConversationMap.put(selectedGroup.getId(), conv);
                }
            } catch (Exception e) {
                errorMessage.set("Không thể mở cuộc trò chuyện nhóm: " + e.getMessage());
                return;
            }
        }

        activeConversation.set(conv);
        if (conv == null || conv.getId() == null) {
            errorMessage.set("Không thể mở cuộc trò chuyện nhóm. Vui lòng thử lại.");
            return;
        }

        markConversationReadAsync(conv);
        conv.setUnreadCount(0);
        conversationVersion.set(conversationVersion.get() + 1);
        loadGroupChatInfo(selectedGroup);
        loadMessages(conv.getId(), 0);
        loadPinnedMessages(conv.getId());
        subscribeToConversation(conv.getId());
    }

    private void beginConversationSwitch(String chatName, boolean isGroup) {
        messageLoadVersion.incrementAndGet();
        pinnedLoadVersion.incrementAndGet();
        activeConversation.set(null);
        currentChatName.set(chatName);
        currentChatIsGroup.set(isGroup);
        typingText.set(null);
        displayedMessageIds.clear();
        messages.clear();
        memberList.clear();
        sentFileList.clear();
        sentLinkList.clear();
        pinnedMessageList.clear();
    }

    private void markConversationReadAsync(ConversationResponse conversation) {
        if (conversation == null || conversation.getId() == null || conversation.getUnreadCount() <= 0) return;
        CompletableFuture.runAsync(() -> {
            try {
                chatService.markConversationAsRead(IdUtils.parseLongId(conversation.getId()), token);
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Không thể đánh dấu cuộc trò chuyện đã đọc: " + conversation.getId(), e);
            }
        });
    }

    private void loadChatInfo(String chatName, boolean isGroup) {
        Platform.runLater(() -> {
            memberList.clear();
            sentFileList.clear();
            sentLinkList.clear();
            pinnedMessageList.clear();

            if (isGroup) {
                memberList.add("Bạn");
            } else {
                memberList.add("Bạn");
                memberList.add(chatName);
            }
        });
    }

    private void loadGroupChatInfo(GroupResponse group) {
        Platform.runLater(() -> {
            memberList.clear();
            sentFileList.clear();
            sentLinkList.clear();
            pinnedMessageList.clear();
        });

        CompletableFuture.runAsync(() -> {
            try {
                GroupMemberResponse[] members = chatService.getGroupMembersList(IdUtils.parseLongId(group.getId()), token);
                if (members != null) {
                    Platform.runLater(() -> {
                        ConversationResponse current = activeConversation.get();
                        if (current == null || !group.getId().equals(current.getGroupId())) return;
                        for (GroupMemberResponse m : members) {
                            String dName = getUserDisplayName(m.getUserId());
                            if ("OWNER".equals(m.getRole())) {
                                dName += " (Chủ nhóm)";
                            } else if ("ADMIN".equals(m.getRole())) {
                                dName += " (Phó nhóm)";
                            }
                            memberList.add(dName);
                        }
                    });
                }
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING, "Không thể tải danh sách thành viên nhóm: " + group.getId(), e);
            }
        });
    }

    private void loadMessages(String conversationId, int unreadCount) {
        long version = messageLoadVersion.incrementAndGet();
        displayedMessageIds.clear();
        messages.clear();

        CompletableFuture.runAsync(() -> {
            try {
                MessageResponse[] history = chatService.getConversationChatHistory(IdUtils.parseLongId(conversationId), token);
                if (history != null) {
                    List<MessageItem> newMessages = new ArrayList<>();
                    List<String> files = new ArrayList<>();
                    List<String> links = new ArrayList<>();
                    Set<String> historyMessageIds = ConcurrentHashMap.newKeySet();

                    for (MessageResponse msg : history) {
                        if (msg.getId() != null && !historyMessageIds.add(msg.getId())) {
                            continue;
                        }
                        boolean isMe = msg.getSenderId() != null && msg.getSenderId().equals(currentUserId);
                        if (!isMe && !"SEEN".equals(msg.getStatus())) {
                            updateMessageStatus(msg, "SEEN");
                        }
                        String timeStr = formatTime(msg.getCreatedAt());
                        String senderName = getUserDisplayName(msg.getSenderId());
                        
                        boolean isDeleted = msg.isDeleted();
                        boolean isDeletedForMe = msg.getDeletedForUsers() != null && msg.getDeletedForUsers().contains(currentUserId);
                        boolean isFile = !"TEXT".equalsIgnoreCase(msg.getMessageType());
                        String content = isFile && msg.getFileName() != null ? msg.getFileName() : msg.getContent();

                        MessageItem item = new MessageItem(msg, senderName, content, timeStr, isMe, isFile, isDeleted, isDeletedForMe);
                        newMessages.add(item);

                        if (isFile && !isDeleted && !isDeletedForMe) {
                            files.add(msg.getFileName() != null ? msg.getFileName() : "file");
                        } else if (!isFile && !isDeleted && !isDeletedForMe) {
                            Matcher matcher = LINK_PATTERN.matcher(msg.getContent());
                            while (matcher.find()) {
                                links.add(matcher.group());
                            }
                        }
                    }

                    Platform.runLater(() -> {
                        ConversationResponse current = activeConversation.get();
                        if (version != messageLoadVersion.get()
                                || current == null || !conversationId.equals(current.getId())) {
                            return;
                        }
                        java.util.LinkedHashMap<String, MessageItem> merged = new java.util.LinkedHashMap<>();
                        int transientIndex = 0;
                        for (MessageItem item : newMessages) {
                            String id = item.getResponse() == null ? null : item.getResponse().getId();
                            merged.put(id == null ? "history-" + transientIndex++ : id, item);
                        }
                        for (MessageItem item : messages) {
                            String id = item.getResponse() == null ? null : item.getResponse().getId();
                            merged.put(id == null ? "current-" + transientIndex++ : id, item);
                        }
                        messages.setAll(merged.values());
                        displayedMessageIds.clear();
                        for (MessageItem item : messages) {
                            if (item.getResponse() != null && item.getResponse().getId() != null) {
                                displayedMessageIds.add(item.getResponse().getId());
                            }
                        }
                        if (messages.isEmpty() && "TRỢ LÝ AI".equals(currentChatName.get())) {
                            messages.add(new MessageItem(null, "TRỢ LÝ AI", "Xin chào! Tôi là Trợ lý AI. Tôi có thể giúp gì cho bạn hôm nay?", getCurrentTime(), false, false, false, false));
                        }
                        sentFileList.setAll(files);
                        sentLinkList.setAll(links);
                    });
                }
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.ERROR, "Lỗi khi tải lịch sử tin nhắn", e);
                Platform.runLater(() -> {
                    ConversationResponse current = activeConversation.get();
                    if (version == messageLoadVersion.get()
                            && current != null && conversationId.equals(current.getId())) {
                        errorMessage.set("Không thể tải lịch sử tin nhắn: " + e.getMessage());
                    }
                });
            }
        });
    }

    private MessageItem toMessageItem(MessageResponse message) {
        boolean isMe = message.getSenderId() != null && message.getSenderId().equals(currentUserId);
        boolean isFile = !"TEXT".equalsIgnoreCase(message.getMessageType());
        String content = isFile && message.getFileName() != null
                ? message.getFileName() : message.getContent();
        return new MessageItem(
                message,
                getUserDisplayName(message.getSenderId()),
                content,
                formatTime(message.getCreatedAt()),
                isMe,
                isFile,
                message.isDeleted(),
                message.getDeletedForUsers() != null
                        && message.getDeletedForUsers().contains(currentUserId));
    }

    public void sendMessage(String text, File file) {
        if (activeConversation.get() == null) {
            errorMessage.set("Vui lòng chọn cuộc trò chuyện trước.");
            return;
        }

        boolean hasText = text != null && !text.trim().isEmpty();
        boolean hasFile = file != null;

        if (!hasText && !hasFile) return;
        
        if (hasFile && file.length() > 104857600L) {
            errorMessage.set("Kích thước file không được vượt quá 100 MB.");
            return;
        }

        boolean isAiChat = "TRỢ LÝ AI".equals(currentChatName.get());
        Long conversationId = IdUtils.parseLongId(activeConversation.get().getId());

        if (hasText) {
            MessageItem pending = createPendingMessage(
                    conversationId, text.trim(), "TEXT", null, null);
            SendMessageRequest request = createTextMessage(conversationId, currentUserId, text);
            realtimeChatService.sendMessage(request)
                    .thenRun(() -> {
                        Platform.runLater(() -> pending.setStatus("SENT"));
                        if (isAiChat) {
                            sendAiResponse(conversationId, text);
                        }
                    })
                    .exceptionally(error -> {
                        Platform.runLater(() -> {
                            pending.setStatus("FAILED");
                            errorMessage.set("Không thể gửi tin nhắn qua WebSocket: " + rootMessage(error));
                        });
                        return null;
                    });
        }

        if (hasFile) {
            if (isAiChat) {
                errorMessage.set("Trợ lý AI hiện chưa hỗ trợ nhận file.");
                return;
            }

            MessageItem pending = createPendingMessage(
                    conversationId, file.getName(), "FILE", file.getName(), file.length());
            CompletableFuture.runAsync(() -> uploadAndSendFile(conversationId, file, pending));
        }
    }

    private MessageItem createPendingMessage(
            Long conversationId, String content, String messageType, String fileName, Long fileSize) {
        MessageResponse pendingResponse = new MessageResponse();
        pendingResponse.setId("pending-" + UUID.randomUUID());
        pendingResponse.setConversationId(String.valueOf(conversationId));
        pendingResponse.setSenderId(currentUserId);
        pendingResponse.setContent(content);
        pendingResponse.setMessageType(messageType);
        pendingResponse.setFileName(fileName);
        pendingResponse.setFileSize(fileSize);
        pendingResponse.setStatus("SENDING");

        MessageItem item = new MessageItem(
                pendingResponse,
                currentUserResponse == null ? "Bạn" : currentUserResponse.getUsername(),
                content,
                getCurrentTime(),
                true,
                !"TEXT".equalsIgnoreCase(messageType),
                false,
                false);
        if (!"TEXT".equalsIgnoreCase(messageType)) item.setUploadProgress(0);
        if (Platform.isFxApplicationThread()) {
            messages.add(item);
        } else {
            Platform.runLater(() -> messages.add(item));
        }
        return item;
    }

    private void handleRealtimeFriend(FriendResponse friend) {
        if (friend == null || friend.getFriendId() == null) return;
        CompletableFuture.runAsync(() -> {
            String displayName = friend.getFriendUsername();
            try {
                UserResponse u = chatService.getUserById(friend.getFriendId(), token);
                if (u != null && u.getFullName() != null && !u.getFullName().isBlank()) {
                    displayName = u.getFullName();
                }
            } catch (Exception ex) {
                // fallback
            }
            if (displayName == null || displayName.isBlank()) {
                displayName = "Người dùng " + friend.getFriendId();
            }
            final String finalDisplayName = displayName;
            Platform.runLater(() -> addFriendToList(friend, finalDisplayName));
        });
    }

    private void addFriendToList(FriendResponse friend, String displayName) {
        if (displayNameToUserId.containsValue(friend.getFriendId())) return;
        UserResponse friendUser = new UserResponse();
        friendUser.setId(friend.getFriendId());
        friendUser.setKeycloakUserId(friend.getFriendId());
        friendUser.setUsername(friend.getFriendUsername());
        friendUser.setFullName(displayName);
        nameToUserMap.put(displayName, friendUser);
        displayNameToUserId.put(displayName, friend.getFriendId());
        privateChatList.add(displayName);
    }

    private void subscribeToRealtimeUpdates() {
        realtimeChatService.subscribeUserMessages(currentUserId, this::handleRealtimeMessage)
                .exceptionally(error -> {
                    Platform.runLater(() -> errorMessage.set(
                            "Không thể theo dõi tin nhắn realtime: " + rootMessage(error)));
                    return null;
                });
        realtimeChatService.subscribeFriends(currentUserId, this::handleRealtimeFriend)
                .exceptionally(error -> {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Không thể theo dõi danh sách bạn bè realtime", error);
                    return null;
                });
    }

    public void close() {
        realtimeChatService.close();
    }

    private void subscribeToConversation(String conversationId) {
        typingText.set(null);
        realtimeChatService.subscribeTyping(conversationId, event -> {
                    if (event.getUserId() == null || event.getUserId().equals(currentUserId)) return;
                    Platform.runLater(() -> typingText.set(event.isTyping()
                            ? (event.getUsername() == null || event.getUsername().isBlank()
                                    ? "Đang nhập..." : event.getUsername() + " đang nhập...")
                            : null));
                })
                .exceptionally(error -> {
                    Platform.runLater(() -> errorMessage.set(
                            "Không thể theo dõi trạng thái nhập: " + rootMessage(error)));
                    return null;
                });
    }

    private void handleRealtimeMessage(MessageResponse message) {
        if (message == null || message.getConversationId() == null) {
            return;
        }

        boolean isMe = currentUserId.equals(message.getSenderId());
        ConversationResponse conversation = findConversation(message.getConversationId());
        if (conversation == null) return;
        boolean firstRealtimeDelivery = message.getId() == null
                || receivedRealtimeMessageIds.add(message.getId());

        ConversationResponse currentConversation = activeConversation.get();
        boolean isActiveConversation = currentConversation != null
                && message.getConversationId().equals(currentConversation.getId());
        boolean isActivelyViewed = isActiveConversation && applicationActive;

        if (!isMe) {
            updateMessageStatus(message, isActivelyViewed ? "SEEN" : "DELIVERED");
            if (firstRealtimeDelivery) {
                Platform.runLater(() -> {
                    ConversationResponse current = activeConversation.get();
                    boolean currentlyOpen = current != null
                            && message.getConversationId().equals(current.getId());
                    if (currentlyOpen) {
                        if (conversation.getUnreadCount() != 0) {
                            conversation.setUnreadCount(0);
                            conversationVersion.set(conversationVersion.get() + 1);
                        }
                    } else {
                        conversation.setUnreadCount(conversation.getUnreadCount() + 1);
                        conversationVersion.set(conversationVersion.get() + 1);
                    }
                    newMessageEvent.set(createNewMessageEvent(message, conversation));
                });
            }
        }

        if (!isActiveConversation) {
            return;
        }

        MessageItem existing = findMessageItem(message.getId());
        if (existing == null && isMe) {
            existing = findMatchingPendingMessage(message);
        }
        if (existing != null) {
            MessageItem target = existing;
            Platform.runLater(() -> {
                ConversationResponse current = activeConversation.get();
                if (current == null || !message.getConversationId().equals(current.getId())) return;
                target.update(message);
                applyPinnedRealtime(message, target);
            });
            return;
        }
        if (message.getId() != null && !displayedMessageIds.add(message.getId())) return;

        boolean isFile = !"TEXT".equalsIgnoreCase(message.getMessageType());
        String content = isFile && message.getFileName() != null
                ? message.getFileName()
                : message.getContent();
        String senderName = "AI_ASSISTANT".equals(message.getSenderId())
                ? "TRỢ LÝ AI"
                : getUserDisplayName(message.getSenderId());

        Platform.runLater(() -> {
            ConversationResponse current = activeConversation.get();
            if (current == null || !message.getConversationId().equals(current.getId())) return;
            messages.add(new MessageItem(
                    message,
                    senderName,
                    content,
                    formatTime(message.getCreatedAt()),
                    isMe,
                    isFile,
                    message.isDeleted(),
                    message.getDeletedForUsers() != null
                            && message.getDeletedForUsers().contains(currentUserId)));
            if (isFile && message.getFileName() != null) {
                sentFileList.add(message.getFileName());
            } else if (content != null) {
                extractAndAddLinks(content);
            }
            applyPinnedRealtime(message, findMessageItem(message.getId()));
        });
    }

    private ConversationResponse findConversation(String conversationId) {
        for (ConversationResponse value : personalConversationMap.values()) {
            if (conversationId.equals(value.getId())) return value;
        }
        for (ConversationResponse value : groupConversationMap.values()) {
            if (conversationId.equals(value.getId())) return value;
        }
        return null;
    }

    private MessageItem findMessageItem(String messageId) {
        if (messageId == null) return null;
        for (MessageItem item : messages) {
            if (item.getResponse() != null && messageId.equals(item.getResponse().getId())) return item;
        }
        return null;
    }

    private MessageItem findMatchingPendingMessage(MessageResponse message) {
        boolean incomingFile = !"TEXT".equalsIgnoreCase(message.getMessageType());
        String incomingContent = incomingFile ? message.getFileName() : message.getContent();
        for (MessageItem item : messages) {
            if (!item.isMe() || item.getResponse() == null
                    || item.getResponse().getId() == null
                    || !item.getResponse().getId().startsWith("pending-")
                    || item.isFile() != incomingFile) {
                continue;
            }
            if (java.util.Objects.equals(item.getContent(), incomingContent)) {
                return item;
            }
        }
        return null;
    }

    private void updateMessageStatus(MessageResponse message, String status) {
        if (message.getId() == null || status.equals(message.getStatus())) return;
        CompletableFuture.runAsync(() -> {
            try {
                MessageStatusRequest request = new MessageStatusRequest();
                request.setUserId(currentUserId);
                request.setStatus(status);
                chatService.updateMessageStatus(IdUtils.parseLongId(message.getId()), request, token);
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING, "Không thể cập nhật trạng thái tin nhắn", e);
            }
        });
    }

    public void sendTyping(boolean typing) {
        ConversationResponse conversation = activeConversation.get();
        if (conversation == null || conversation.getId() == null) return;
        TypingRequest request = new TypingRequest();
        request.setConversationId(IdUtils.parseLongId(conversation.getId()));
        request.setUserId(currentUserId);
        request.setUsername(currentUserResponse == null ? null : currentUserResponse.getUsername());
        request.setTyping(typing);
        realtimeChatService.sendTyping(request);
    }

    private SendMessageRequest createTextMessage(Long conversationId, String senderId, String content) {
        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(conversationId);
        request.setSenderId(senderId);
        request.setContent(content);
        request.setMessageType("TEXT");
        return request;
    }

    private void sendAiResponse(Long conversationId, String question) {
        Platform.runLater(() -> aiLoading.set(true));
        CompletableFuture.runAsync(() -> {
            String answer = aiService.callAIAssistant(question);
            realtimeChatService.sendMessage(createTextMessage(conversationId, "AI_ASSISTANT", answer))
                    .whenComplete((ignored, error) -> Platform.runLater(() -> aiLoading.set(false)))
                    .exceptionally(error -> {
                        Platform.runLater(() -> errorMessage.set(
                                "Không thể gửi phản hồi AI qua WebSocket: " + rootMessage(error)));
                        return null;
                    });
        });
    }

    private void uploadAndSendFile(Long conversationId, File file, MessageItem pending) {
        try {
            LOGGER.log(System.Logger.Level.INFO, "Bắt đầu upload file: {0}, size={1}",
                    file.getName(), file.length());
            AtomicInteger lastPercent = new AtomicInteger(-1);
            String uploadedFileUrl = chatService.uploadFile(file, token, progress -> {
                int percent = (int) Math.round(progress * 100);
                if (lastPercent.getAndSet(percent) != percent) {
                    Platform.runLater(() -> pending.setUploadProgress(percent / 100d));
                }
            });

            SendMessageRequest request = new SendMessageRequest();
            request.setConversationId(conversationId);
            request.setSenderId(currentUserId);
            request.setFileName(file.getName());
            request.setFileSize(file.length());
            request.setFileUrl(uploadedFileUrl);
            request.setFileType(FileUtils.getFileExtension(file));

            String extension = FileUtils.getFileExtension(file).toLowerCase();
            request.setMessageType(
                    extension.equals("png") || extension.equals("jpg")
                            || extension.equals("jpeg") || extension.equals("gif")
                            ? "IMAGE"
                            : "FILE");

            realtimeChatService.sendMessage(request).join();
            Platform.runLater(() -> {
                pending.setUploadProgress(1);
                pending.setStatus("SENT");
            });
        } catch (Exception error) {
            LOGGER.log(System.Logger.Level.ERROR, "Lỗi gửi file", error);
            Platform.runLater(() -> {
                pending.setStatus("FAILED");
                errorMessage.set("Không thể gửi file qua WebSocket: " + rootMessage(error));
            });
        }
    }

    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    private void extractAndAddLinks(String text) {
        Matcher matcher = LINK_PATTERN.matcher(text);
        while (matcher.find()) {
            sentLinkList.add(matcher.group());
        }
    }

    public void createGroup(String groupName, String groupDesc) {
        try {
            CreateGroupRequest req = new CreateGroupRequest();
            req.setName(groupName);
            req.setDescription(groupDesc);
            req.setCreatorId(currentUserId);
            req.setMemberIds(new ArrayList<>(List.of(currentUserId)));

            GroupResponse group = chatService.createGroup(req, token);
            Platform.runLater(() -> {
                nameToGroupMap.put(groupName, group);
                groupChatList.add(groupName);
            });

            ConversationResponse conv = chatService.createGroupConversation(IdUtils.parseLongId(group.getId()), token);
            Platform.runLater(() -> groupConversationMap.put(group.getId(), conv));

            notificationMessage.set("Đã tạo nhóm " + groupName);
        } catch (Exception e) {
            errorMessage.set("Không thể tạo nhóm: " + e.getMessage());
        }
    }

    public void addFriend(String username) {
        CompletableFuture.runAsync(() -> {
            try {
                AddFriendRequest request = new AddFriendRequest();
                request.setUserId(currentUserId);
                request.setUsername(username.trim());

                FriendResponse friend = chatService.addFriend(request, token);
                if (friend == null || friend.getFriendId() == null) {
                    Platform.runLater(() -> errorMessage.set("Không thể thêm bạn. Vui lòng thử lại."));
                    return;
                }

                String displayName = friend.getFriendUsername();
                try {
                    UserResponse u = chatService.getUserById(friend.getFriendId(), token);
                    if (u != null && u.getFullName() != null && !u.getFullName().isBlank()) {
                        displayName = u.getFullName();
                    }
                } catch (Exception ex) {
                    // fallback
                }
                if (displayName == null || displayName.isBlank()) {
                    displayName = "Người dùng " + friend.getFriendId();
                }
                final String finalDisplayName = displayName;
                Platform.runLater(() -> {
                    addFriendToList(friend, finalDisplayName);
                    notificationMessage.set("Đã thêm bạn: " + finalDisplayName);
                });
            } catch (Exception e) {
                Platform.runLater(() -> errorMessage.set("Không thể thêm bạn: " + e.getMessage()));
            }
        });
    }

    public void addGroupMember(String selectedUserStr) {
        UserResponse user = nameToUserMap.get(selectedUserStr);
        if (user != null && activeConversation.get() != null) {
            try {
                AddGroupMemberRequest req = new AddGroupMemberRequest();
                req.setUserId(user.getId());
                req.setInvitedBy(currentUserId);
                req.setRole("MEMBER");

                chatService.addGroupMember(IdUtils.parseLongId(activeConversation.get().getGroupId()), req, token);
                Platform.runLater(() -> memberList.add(selectedUserStr));
                notificationMessage.set("Đã thêm " + selectedUserStr + " vào nhóm.");
            } catch (Exception e) {
                errorMessage.set("Không thể thêm thành viên: " + e.getMessage());
            }
        }
    }

    public void leaveGroup() {
        if (activeConversation.get() != null && activeConversation.get().getGroupId() != null) {
            try {
                chatService.removeGroupMember(IdUtils.parseLongId(activeConversation.get().getGroupId()), currentUserId, token);

                Platform.runLater(() -> {
                    groupChatList.remove(currentChatName.get());
                    nameToGroupMap.remove(currentChatName.get());
                    messages.clear();
                    memberList.clear();
                    sentFileList.clear();
                    sentLinkList.clear();
                    currentChatName.set(null);
                    currentChatIsGroup.set(false);
                    activeConversation.set(null);
                });

                notificationMessage.set("Bạn đã rời khỏi nhóm.");
            } catch (Exception e) {
                errorMessage.set("Không thể rời nhóm: " + e.getMessage());
            }
        }
    }

    public void kickMember(String memberName) {
        GroupResponse group = nameToGroupMap.get(currentChatName.get());
        if (group == null) return;

        try {
            String targetUserId = null;
            GroupMemberResponse[] members = chatService.getGroupMembersList(IdUtils.parseLongId(group.getId()), token);
            if (members != null) {
                for (GroupMemberResponse m : members) {
                    String displayName = getUserDisplayName(m.getUserId());
                    String displayNameWithRole = displayName;
                    if ("OWNER".equals(m.getRole())) {
                        displayNameWithRole += " (Chủ nhóm)";
                    } else if ("ADMIN".equals(m.getRole())) {
                        displayNameWithRole += " (Phó nhóm)";
                    }
                    
                    if (displayNameWithRole.equals(memberName)) {
                        targetUserId = m.getUserId();
                        break;
                    }
                }
            }

            if (targetUserId == null) {
                errorMessage.set("Không tìm thấy thông tin thành viên.");
                return;
            }

            chatService.removeGroupMember(IdUtils.parseLongId(group.getId()), targetUserId, token);
            Platform.runLater(() -> memberList.remove(memberName));
            notificationMessage.set("Đã kick " + memberName + " khỏi nhóm.");
        } catch (Exception e) {
            errorMessage.set("Không thể kick thành viên: " + e.getMessage());
        }
    }

    public void updateRole(String memberName, String newRole) {
        GroupResponse group = nameToGroupMap.get(currentChatName.get());
        if (group == null) return;

        try {
            String targetUserId = null;
            GroupMemberResponse[] members = chatService.getGroupMembersList(IdUtils.parseLongId(group.getId()), token);
            if (members != null) {
                for (GroupMemberResponse m : members) {
                    if (getUserDisplayName(m.getUserId()).equals(memberName) || 
                        ("Bạn".equals(memberName) && m.getUserId().equals(currentUserId))) {
                        targetUserId = m.getUserId();
                        break;
                    }
                }
            }

            if (targetUserId == null) {
                errorMessage.set("Không tìm thấy thông tin thành viên.");
                return;
            }

            UpdateMemberRoleRequest req = new UpdateMemberRoleRequest();
            req.setRole(newRole);
            chatService.updateMemberRole(IdUtils.parseLongId(group.getId()), targetUserId, req, token);
            
            loadGroupChatInfo(group);
            notificationMessage.set("Đã cập nhật quyền của " + memberName + " thành " + ("ADMIN".equals(newRole) ? "Phó nhóm" : "Thành viên") + ".");
        } catch (Exception e) {
            errorMessage.set("Không thể cập nhật quyền: " + e.getMessage());
        }
    }

    public void deleteMessageForUser(MessageResponse msg, MessageItem item) {
        try {
            chatService.deleteMessageForUser(IdUtils.parseLongId(msg.getId()), currentUserId, token);
            Platform.runLater(() -> {
                item.setDeletedForMe(true);
            });
        } catch (Exception e) {
            errorMessage.set("Không thể xóa tin nhắn: " + e.getMessage());
        }
    }

    public void recallMessage(MessageResponse msg, MessageItem item) {
        try {
            chatService.recallMessage(IdUtils.parseLongId(msg.getId()), currentUserId, token);
            Platform.runLater(() -> {
                item.setDeleted(true);
            });
        } catch (Exception e) {
            errorMessage.set("Không thể thu hồi tin nhắn: " + e.getMessage());
        }
    }

    public void removeFriend(String displayName) {
        if (displayName == null || "TRỢ LÝ AI".equals(displayName)) return;
        UserResponse friend = nameToUserMap.get(displayName);
        String friendId = friend != null ? friend.getId() : displayNameToUserId.get(displayName);
        if (friendId == null) {
            errorMessage.set("Không tìm thấy thông tin bạn bè.");
            return;
        }
        try {
            chatService.removeFriend(currentUserId, friendId, token);
            Platform.runLater(() -> {
                privateChatList.remove(displayName);
                nameToUserMap.remove(displayName);
                displayNameToUserId.remove(displayName);
                if (displayName.equals(currentChatName.get())) {
                    messages.clear();
                    currentChatName.set(null);
                    activeConversation.set(null);
                }
            });
            notificationMessage.set("Đã xóa " + displayName + " khỏi danh sách bạn bè.");
        } catch (Exception e) {
            errorMessage.set("Không thể xóa bạn bè: " + e.getMessage());
        }
    }

    public java.util.List<GroupMemberView> getCurrentGroupMembers() {
        GroupResponse group = nameToGroupMap.get(currentChatName.get());
        if (group == null) return java.util.List.of();
        try {
            GroupMemberResponse[] members = chatService.getGroupMembersList(IdUtils.parseLongId(group.getId()), token);
            if (members == null) return java.util.List.of();
            return java.util.Arrays.stream(members)
                    .map(member -> new GroupMemberView(member.getUserId(),
                            member.getUserId().equals(currentUserId) ? "Bạn" : getUserDisplayName(member.getUserId()),
                            member.getRole()))
                    .toList();
        } catch (Exception e) {
            errorMessage.set("Không thể tải thành viên nhóm: " + e.getMessage());
            return java.util.List.of();
        }
    }

    public java.util.List<String> getAvailableGroupMemberNames() {
        java.util.Set<String> existingIds = getCurrentGroupMembers().stream()
                .map(GroupMemberView::userId).collect(java.util.stream.Collectors.toSet());
        return nameToUserMap.entrySet().stream()
                .filter(entry -> !existingIds.contains(entry.getValue().getId()))
                .map(java.util.Map.Entry::getKey).sorted().toList();
    }

    public void removeGroupMemberById(String userId) {
        GroupResponse group = nameToGroupMap.get(currentChatName.get());
        if (group == null || !isGroupCreator(currentChatName.get()) || userId.equals(currentUserId)) return;
        try {
            chatService.removeGroupMember(IdUtils.parseLongId(group.getId()), userId, token);
            loadGroupChatInfo(group);
            notificationMessage.set("Đã xóa thành viên khỏi nhóm.");
        } catch (Exception e) {
            errorMessage.set("Không thể xóa thành viên: " + e.getMessage());
        }
    }

    public void transferGroupOwnership(String newOwnerId) {
        GroupResponse group = nameToGroupMap.get(currentChatName.get());
        if (group == null || !isGroupCreator(currentChatName.get()) || newOwnerId.equals(currentUserId)) return;
        try {
            GroupResponse updated = chatService.transferGroupOwnership(
                    IdUtils.parseLongId(group.getId()), currentUserId, newOwnerId, token);
            nameToGroupMap.put(currentChatName.get(), updated);
            loadGroupChatInfo(updated);
            notificationMessage.set("Đã chuyển quyền chủ nhóm.");
        } catch (Exception e) {
            errorMessage.set("Không thể chuyển quyền chủ nhóm: " + e.getMessage());
        }
    }

    public void deleteCurrentGroup() {
        GroupResponse group = nameToGroupMap.get(currentChatName.get());
        if (group == null || !currentUserId.equals(group.getCreatorId())) {
            errorMessage.set("Chỉ chủ nhóm mới có thể xóa nhóm.");
            return;
        }
        try {
            chatService.deleteGroup(IdUtils.parseLongId(group.getId()), currentUserId, token);
            Platform.runLater(() -> {
                groupChatList.remove(group.getName());
                nameToGroupMap.remove(group.getName());
                groupConversationMap.remove(group.getId());
                messages.clear();
                memberList.clear();
                sentFileList.clear();
                sentLinkList.clear();
                pinnedMessageList.clear();
                currentChatName.set(null);
                currentChatIsGroup.set(false);
                activeConversation.set(null);
            });
            notificationMessage.set("Đã xóa nhóm " + group.getName() + ".");
        } catch (Exception e) {
            errorMessage.set("Không thể xóa nhóm: " + e.getMessage());
        }
    }

    public void editMessage(MessageItem item, String content) {
        if (item == null || item.getResponse() == null) return;
        CompletableFuture.runAsync(() -> {
            try {
                UpdateMessageRequest request = new UpdateMessageRequest();
                request.setUserId(currentUserId);
                request.setContent(content);
                chatService.editMessage(IdUtils.parseLongId(item.getResponse().getId()), request, token);
            } catch (Exception e) {
                Platform.runLater(() -> errorMessage.set("Không thể chỉnh sửa tin nhắn: " + e.getMessage()));
            }
        });
    }

    public void toggleStar(MessageItem item) {
        if (item == null || item.getResponse() == null) return;
        CompletableFuture.runAsync(() -> {
            try {
                chatService.setMessageStarred(IdUtils.parseLongId(item.getResponse().getId()),
                        !item.isStarred(), token);
            } catch (Exception e) {
                Platform.runLater(() -> errorMessage.set("Không thể đánh dấu sao: " + e.getMessage()));
            }
        });
    }

    public void togglePin(MessageItem item) {
        if (item == null || item.getResponse() == null) return;
        boolean previous = item.isPinned();
        boolean target = !previous;
        Platform.runLater(() -> {
            item.setPinned(target);
            applyPinnedState(item);
        });
        CompletableFuture.runAsync(() -> {
            try {
                chatService.setMessagePinned(
                        IdUtils.parseLongId(item.getResponse().getId()), target, token);
                Platform.runLater(() -> {
                    item.setPinned(target);
                    applyPinnedState(item);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    item.setPinned(previous);
                    applyPinnedState(item);
                    errorMessage.set("Không thể cập nhật ghim tin nhắn: " + e.getMessage());
                });
            }
        });
    }

    public void unpinMessage(PinnedMessageItem pinned) {
        if (pinned == null || pinned.message() == null || !pinned.message().isPinned()) return;
        MessageItem item = pinned.message();
        CompletableFuture.runAsync(() -> {
            try {
                chatService.setMessagePinned(
                        IdUtils.parseLongId(pinned.messageId()), false, token);
                Platform.runLater(() -> {
                    item.setPinned(false);
                    applyPinnedState(item);
                    MessageItem loaded = findMessageItem(pinned.messageId());
                    if (loaded != null) loaded.setPinned(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> errorMessage.set(
                        "Không thể bỏ ghim tin nhắn: " + e.getMessage()));
            }
        });
    }

    public MessageItem searchConversationMessage(String query) {
        if (activeConversation.get() == null || query == null || query.isBlank()) return null;
        String normalized = query.trim().toLowerCase();
        for (MessageItem item : messages) {
            if (!item.isDeleted() && !item.isDeletedForMe()
                    && item.getContent() != null
                    && item.getContent().toLowerCase().contains(normalized)) {
                return item;
            }
        }
        return null;
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

    private void loadPinnedMessages(String conversationId) {
        loadPinnedMessages(conversationId, true);
    }

    private void loadPinnedMessages(String conversationId, boolean clearFirst) {
        long version = pinnedLoadVersion.incrementAndGet();
        if (clearFirst) Platform.runLater(pinnedMessageList::clear);
        if (conversationId == null) return;

        CompletableFuture.runAsync(() -> {
            try {
                MessageResponse[] responses = chatService.getPinnedMessages(
                        IdUtils.parseLongId(conversationId), token);
                java.util.LinkedHashMap<String, PinnedMessageItem> unique = new java.util.LinkedHashMap<>();
                if (responses != null) {
                    for (MessageResponse response : responses) {
                        if (!conversationId.equals(response.getConversationId())
                                || response.getId() == null || !response.isPinned()
                                || response.isDeleted()
                                || response.getDeletedForUsers() != null
                                && response.getDeletedForUsers().contains(currentUserId)) {
                            continue;
                        }
                        MessageItem item = toMessageItem(response);
                        item.setPinned(true);
                        unique.put(response.getId(), new PinnedMessageItem(item));
                    }
                }
                Platform.runLater(() -> {
                    ConversationResponse current = activeConversation.get();
                    if (version != pinnedLoadVersion.get()
                            || current == null
                            || !conversationId.equals(current.getId())) {
                        return;
                    }
                    pinnedMessageList.setAll(unique.values());
                });
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Không thể tải danh sách tin nhắn ghim", e);
                Platform.runLater(() -> {
                    if (version == pinnedLoadVersion.get()) pinnedMessageList.clear();
                });
            }
        });
    }

    private void applyPinnedRealtime(MessageResponse response, MessageItem item) {
        ConversationResponse current = activeConversation.get();
        if (current == null || response == null
                || !current.getId().equals(response.getConversationId())
                || response.getId() == null) {
            return;
        }
        boolean currentlyPinned = pinnedMessageList.stream()
                .anyMatch(pinned -> response.getId().equals(pinned.messageId()));
        if (currentlyPinned == response.isPinned()) return;
        MessageItem resolved = item != null ? item : toMessageItem(response);
        resolved.setPinned(response.isPinned());
        applyPinnedState(resolved);
        loadPinnedMessages(current.getId(), false);
    }

    private void applyPinnedState(MessageItem item) {
        if (item == null || item.getResponse() == null || item.getResponse().getId() == null) return;
        String messageId = item.getResponse().getId();
        pinnedMessageList.removeIf(pinned -> messageId.equals(pinned.messageId()));
        if (item.isPinned() && !item.isDeleted() && !item.isDeletedForMe()) {
            pinnedMessageList.add(new PinnedMessageItem(item));
        }
    }

    public void clearConversationData() {
        messageLoadVersion.incrementAndGet();
        pinnedLoadVersion.incrementAndGet();
        Runnable clear = () -> {
            messages.clear();
            memberList.clear();
            sentFileList.clear();
            sentLinkList.clear();
            pinnedMessageList.clear();
            currentChatName.set(null);
            currentChatIsGroup.set(false);
            activeConversation.set(null);
        };
        if (Platform.isFxApplicationThread()) clear.run();
        else Platform.runLater(clear);
    }

    public byte[] downloadFile(MessageResponse msg) throws Exception {
        return chatService.downloadMessageFile(IdUtils.parseLongId(msg.getId()), token);
    }

    public CompletableFuture<MessageItem> ensureMessageLoaded(String messageId) {
        MessageItem existing = findMessageItem(messageId);
        if (existing != null) return CompletableFuture.completedFuture(existing);
        ConversationResponse conversation = activeConversation.get();
        if (conversation == null || messageId == null) {
            return CompletableFuture.completedFuture(null);
        }
        String conversationId = conversation.getId();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return chatService.getMessagesAround(IdUtils.parseLongId(messageId), 20, token);
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }).thenCompose(responses -> {
            CompletableFuture<MessageItem> result = new CompletableFuture<>();
            Platform.runLater(() -> {
                ConversationResponse current = activeConversation.get();
                if (current == null || !conversationId.equals(current.getId())) {
                    result.complete(null);
                    return;
                }
                java.util.LinkedHashMap<String, MessageItem> merged = new java.util.LinkedHashMap<>();
                int transientIndex = 0;
                for (MessageItem currentItem : messages) {
                    if (currentItem.getResponse() != null && currentItem.getResponse().getId() != null) {
                        merged.put(currentItem.getResponse().getId(), currentItem);
                    } else {
                        merged.put("transient-" + transientIndex++, currentItem);
                    }
                }
                if (responses != null) {
                    for (MessageResponse response : responses) {
                        if (conversationId.equals(response.getConversationId())
                                && response.getId() != null) {
                            merged.putIfAbsent(response.getId(), toMessageItem(response));
                        }
                    }
                }
                List<MessageItem> ordered = new ArrayList<>(merged.values());
                ordered.sort(java.util.Comparator.comparing(
                        value -> value.getResponse() == null
                                ? null : value.getResponse().getCreatedAt(),
                        java.util.Comparator.nullsLast(String::compareTo)));
                messages.setAll(ordered);
                result.complete(findMessageItem(messageId));
            });
            return result;
        }).exceptionally(error -> {
            Platform.runLater(() -> errorMessage.set(
                    "Không thể tải tin nhắn gốc: " + rootMessage(error)));
            return null;
        });
    }

    public void performSearch(String keyword) {
        Platform.runLater(() -> {
            privateChatList.clear();
            for (String key : nameToUserMap.keySet()) {
                // Skip AI Assistant in search results
                if ("TRỢ LÝ AI".equals(key)) continue;
                if (key.toLowerCase().contains(keyword.toLowerCase())) {
                    privateChatList.add(key);
                }
            }

            groupChatList.clear();
            for (String key : nameToGroupMap.keySet()) {
                if (key.toLowerCase().contains(keyword.toLowerCase())) {
                    groupChatList.add(key);
                }
            }
        });
    }

    private String formatTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) return "";
        try {
            if (timeStr.contains("T")) {
                return timeStr.substring(timeStr.indexOf("T") + 1, timeStr.indexOf("T") + 6);
            }
            return timeStr;
        } catch (Exception e) {
            return "";
        }
    }

    private String getCurrentTime() {
        return java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
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
    public IntegerProperty conversationVersionProperty() { return conversationVersion; }
    public ObjectProperty<NewMessageEvent> newMessageEventProperty() { return newMessageEvent; }

    public String getCurrentUserId() { return currentUserId; }
    public String getToken() { return token; }

    public CompletableFuture<UserResponse> loadCurrentUserProfile() {
        String keycloakUserId = currentUserResponse == null
                ? null : currentUserResponse.getKeycloakUserId();
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Không tìm thấy thông tin tài khoản hiện tại."));
        }
        System.out.println("[Profile] loadCurrentUserProfile start keycloakUserId=" + keycloakUserId);
        return CompletableFuture.supplyAsync(() -> {
            try {
                UserResponse profile = chatService.getCurrentUserProfile(token);
                System.out.println("[Profile] loadCurrentUserProfile got current user profile id="
                        + profile.getId() + ", username=" + profile.getUsername()
                        + ", keycloakUserId=" + profile.getKeycloakUserId());
                return profile;
            } catch (Exception e) {
                System.out.println("[Profile] loadCurrentUserProfile getCurrentUserProfile failed: " + rootMessage(e));
                try {
                    UserResponse profile = chatService.getUserById(keycloakUserId, token);
                    System.out.println("[Profile] loadCurrentUserProfile fallback by id succeeded: id="
                            + profile.getId() + ", username=" + profile.getUsername()
                            + ", keycloakUserId=" + profile.getKeycloakUserId());
                    return profile;
                } catch (Exception e2) {
                    System.out.println("[Profile] loadCurrentUserProfile getUserById fallback failed: " + rootMessage(e2));
                    String username = currentUserResponse == null
                            ? null : currentUserResponse.getUsername();
                    if (username == null || username.isBlank()) {
                        throw new java.util.concurrent.CompletionException(e2);
                    }
                    try {
                        UserResponse profile = chatService.getUserByUsername(username, token);
                        System.out.println("[Profile] loadCurrentUserProfile fallback by username succeeded: username="
                                + profile.getUsername() + ", keycloakUserId=" + profile.getKeycloakUserId());
                        return profile;
                    } catch (Exception fallbackError) {
                        fallbackError.addSuppressed(e2);
                        throw new java.util.concurrent.CompletionException(fallbackError);
                    }
                }
            }
        }).thenApply(profile -> {
            currentUserResponse = profile;
            return profile;
        });
    }

    public UserResponse getCurrentUserProfileSnapshot() {
        return currentUserResponse;
    }

    public CompletableFuture<UserResponse> loadGroupMemberProfile(String userId) {
        if (userId == null || userId.isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Không tìm thấy thành viên."));
        }
        System.out.println("[Profile] loadGroupMemberProfile start userId=" + userId);
        return CompletableFuture.supplyAsync(() -> {
            UserProfileResponse chatProfile = null;
            try {
                try {
                    chatProfile = chatService.getUserProfileById(userId, token);
                } catch (Exception pe) {
                    System.out.println("[Profile] loadGroupMemberProfile getUserProfileById failed: " + rootMessage(pe));
                }
                System.out.println("[Profile] loadGroupMemberProfile chatProfile: userId=" + userId
                        + ", externalSub=" + (chatProfile == null ? "null" : chatProfile.getExternalSub())
                        + ", username=" + (chatProfile == null ? "null" : chatProfile.getUsername()));
                String keycloakUserId = chatProfile == null ? null : chatProfile.getExternalSub();
                if (keycloakUserId == null || keycloakUserId.isBlank()) {
                    keycloakUserId = userId;
                }
                UserResponse profile = chatService.getUserById(keycloakUserId, token);
                System.out.println("[Profile] loadGroupMemberProfile got user profile: username="
                        + profile.getUsername() + ", keycloakUserId=" + profile.getKeycloakUserId());
                return profile;
            } catch (Exception e) {
                System.out.println("[Profile] loadGroupMemberProfile failed userId=" + userId
                        + ", error=" + rootMessage(e));
                if (chatProfile != null && chatProfile.getUsername() != null && !chatProfile.getUsername().isBlank()) {
                    try {
                        UserResponse profile = chatService.getUserByUsername(chatProfile.getUsername(), token);
                        System.out.println("[Profile] loadGroupMemberProfile fallback by username succeeded: username="
                                + profile.getUsername() + ", keycloakUserId=" + profile.getKeycloakUserId());
                        return profile;
                    } catch (Exception fallbackError) {
                        fallbackError.addSuppressed(e);
                        throw new java.util.concurrent.CompletionException(fallbackError);
                    }
                }
                throw new java.util.concurrent.CompletionException(e);
            }
        });
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
        if (currentUserResponse == null || currentUserResponse.getKeycloakUserId() == null
                || currentUserResponse.getKeycloakUserId().isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Không tìm thấy thông tin tài khoản hiện tại."));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return chatService.updateCurrentUserProfile(request, token);
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }).thenApply(profile -> {
            currentUserResponse = profile;
            return profile;
        });
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

    public static class MessageItem {
        private final MessageResponse response;
        private final String senderName;
        private final StringProperty content = new SimpleStringProperty();
        private final String time;
        private final boolean isMe;
        private final boolean isFile;
        private final BooleanProperty isDeleted = new SimpleBooleanProperty();
        private final BooleanProperty isDeletedForMe = new SimpleBooleanProperty();
        private final StringProperty status = new SimpleStringProperty("SENT");
        private final BooleanProperty starred = new SimpleBooleanProperty();
        private final BooleanProperty pinned = new SimpleBooleanProperty();
        private final DoubleProperty uploadProgress = new SimpleDoubleProperty(-1);

        public MessageItem(MessageResponse response, String senderName, String content, String time, boolean isMe, boolean isFile, boolean isDeleted, boolean isDeletedForMe) {
            this.response = response;
            this.senderName = senderName;
            this.content.set(content);
            this.time = time;
            this.isMe = isMe;
            this.isFile = isFile;
            this.isDeleted.set(isDeleted);
            this.isDeletedForMe.set(isDeletedForMe);
            if (response != null) {
                this.status.set(response.getStatus() == null ? "SENT" : response.getStatus());
                this.starred.set(response.isStarred());
                this.pinned.set(response.isPinned());
            }
        }

        public MessageResponse getResponse() { return response; }
        public String getSenderName() { return senderName; }
        public String getContent() { return content.get(); }
        public StringProperty contentProperty() { return content; }
        public String getTime() { return time; }
        public boolean isMe() { return isMe; }
        public boolean isFile() { return isFile; }
        
        public boolean isDeleted() { return isDeleted.get(); }
        public void setDeleted(boolean value) { this.isDeleted.set(value); }
        public BooleanProperty isDeletedProperty() { return isDeleted; }

        public boolean isDeletedForMe() { return isDeletedForMe.get(); }
        public void setDeletedForMe(boolean value) { this.isDeletedForMe.set(value); }
        public BooleanProperty isDeletedForMeProperty() { return isDeletedForMe; }
        public String getStatus() { return status.get(); }
        public void setStatus(String value) { status.set(value); }
        public StringProperty statusProperty() { return status; }
        public boolean isStarred() { return starred.get(); }
        public BooleanProperty starredProperty() { return starred; }
        public boolean isPinned() { return pinned.get(); }
        public void setPinned(boolean value) {
            pinned.set(value);
            if (response != null) response.setPinned(value);
        }
        public BooleanProperty pinnedProperty() { return pinned; }
        public double getUploadProgress() { return uploadProgress.get(); }
        public void setUploadProgress(double value) { uploadProgress.set(value); }
        public DoubleProperty uploadProgressProperty() { return uploadProgress; }

        public void update(MessageResponse updated) {
            response.setId(updated.getId());
            response.setConversationId(updated.getConversationId());
            response.setSenderId(updated.getSenderId());
            response.setContent(updated.getContent());
            response.setFileUrl(updated.getFileUrl());
            response.setFileName(updated.getFileName());
            response.setFileSize(updated.getFileSize());
            response.setFileType(updated.getFileType());
            response.setMessageType(updated.getMessageType());
            response.setUpdatedAt(updated.getUpdatedAt());
            response.setDeleted(updated.isDeleted());
            response.setDeletedForUsers(updated.getDeletedForUsers());
            response.setStatus(updated.getStatus());
            response.setStarred(updated.isStarred());
            response.setPinned(updated.isPinned());
            response.setEditedAt(updated.getEditedAt());
            if (!isFile) content.set(updated.getContent());
            isDeleted.set(updated.isDeleted());
            status.set(updated.getStatus() == null ? "SENT" : updated.getStatus());
            starred.set(updated.isStarred());
            pinned.set(updated.isPinned());
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
