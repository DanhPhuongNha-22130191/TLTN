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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatViewModel {

    private static final System.Logger LOGGER = System.getLogger(ChatViewModel.class.getName());

    private final ChatService chatService;
    private final AIService aiService;
    private final RealtimeChatService realtimeChatService;
    private final Set<String> displayedMessageIds = ConcurrentHashMap.newKeySet();
    
    private String token;
    private String currentUserId;
    private UserResponse currentUserResponse;

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
    private final ObservableList<String> pinnedMessageList = FXCollections.observableArrayList();

    private final StringProperty currentChatName = new SimpleStringProperty();
    private final BooleanProperty currentChatIsGroup = new SimpleBooleanProperty(false);
    private final ObjectProperty<ConversationResponse> activeConversation = new SimpleObjectProperty<>();

    private final BooleanProperty isLoading = new SimpleBooleanProperty(false);
    private final StringProperty errorMessage = new SimpleStringProperty();
    private final StringProperty notificationMessage = new SimpleStringProperty();
    private final StringProperty typingText = new SimpleStringProperty();
    private final BooleanProperty aiLoading = new SimpleBooleanProperty(false);
    private final IntegerProperty conversationVersion = new SimpleIntegerProperty();

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
            
            privateChatList.add("TRỢ LÝ AI");
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
                                if (!displayNameToUserId.containsValue(otherUserId)) {
                                    String displayName = fetchUserDisplayName(otherUserId);
                                    String finalDisplayName = displayName;
                                    String finalOtherUserId = otherUserId;
                                    Platform.runLater(() -> {
                                        if (!nameToUserMap.containsKey(finalDisplayName)) {
                                            UserResponse placeholderUser = new UserResponse();
                                            placeholderUser.setId(finalOtherUserId);
                                            placeholderUser.setUsername(finalDisplayName);
                                            placeholderUser.setFullName(finalDisplayName);
                                            nameToUserMap.put(finalDisplayName, placeholderUser);
                                            displayNameToUserId.put(finalDisplayName, finalOtherUserId);
                                            privateChatList.add(finalDisplayName);
                                        }
                                    });
                                }
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
                Platform.runLater(() -> {
                    for (FriendResponse friend : friends) {
                        String displayName = friend.getFriendUsername() != null && !friend.getFriendUsername().isBlank()
                                ? friend.getFriendUsername()
                                : "Người dùng " + friend.getFriendId();

                        addFriendToList(friend, displayName);
                    }
                });
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    private String fetchUserDisplayName(String userId) {
        String displayName = null;
        try {
            UserProfileResponse profile = chatService.getUserProfileById(userId, token);
            if (profile != null && profile.getUsername() != null && !profile.getUsername().isBlank()) {
                displayName = profile.getUsername();
            } else {
                profile = chatService.getUserProfileByExternalSub(userId, token);
                if (profile != null && profile.getUsername() != null && !profile.getUsername().isBlank()) {
                    displayName = profile.getUsername();
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

        currentChatName.set(selectedUserStr);
        currentChatIsGroup.set(false);

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

        if (conv.getUnreadCount() > 0) {
            try {
                chatService.markConversationAsRead(IdUtils.parseLongId(conv.getId()), token);
            } catch (Exception e) {}
        }
        
        conv.setUnreadCount(0);
        conversationVersion.set(conversationVersion.get() + 1);
        loadChatInfo(selectedUserStr, false);
        loadMessages(conv.getId(), 0); // Ignore unreadCount for loading as it's just marked 0
        subscribeToConversation(conv.getId());
    }

    public void selectGroupChat(String selectedGroupStr) {
        GroupResponse selectedGroup = nameToGroupMap.get(selectedGroupStr);
        if (selectedGroup == null) return;

        currentChatName.set(selectedGroupStr);
        currentChatIsGroup.set(true);

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

        if (conv.getUnreadCount() > 0) {
            try {
                chatService.markConversationAsRead(IdUtils.parseLongId(conv.getId()), token);
            } catch (Exception e) {}
        }
        
        conv.setUnreadCount(0);
        conversationVersion.set(conversationVersion.get() + 1);
        loadGroupChatInfo(selectedGroup);
        loadMessages(conv.getId(), 0);
        subscribeToConversation(conv.getId());
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
        displayedMessageIds.clear();
        Platform.runLater(messages::clear);

        CompletableFuture.runAsync(() -> {
            try {
                MessageResponse[] history = chatService.getConversationChatHistory(IdUtils.parseLongId(conversationId), token);
                if (history != null) {
                    List<MessageItem> newMessages = new ArrayList<>();
                    List<String> files = new ArrayList<>();
                    List<String> links = new ArrayList<>();

                    for (MessageResponse msg : history) {
                        if (msg.getId() != null && !displayedMessageIds.add(msg.getId())) {
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
                        if (msg.isPinned() && content != null) {
                            String summary = pinnedSummary(content);
                            if (!pinnedMessageList.contains(summary)) {
                                Platform.runLater(() -> pinnedMessageList.add(summary));
                            }
                        }

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
                        if (current == null || !conversationId.equals(current.getId())) {
                            return;
                        }
                        messages.addAll(newMessages);
                        if (messages.isEmpty() && "TRỢ LÝ AI".equals(currentChatName.get())) {
                            messages.add(new MessageItem(null, "TRỢ LÝ AI", "Xin chào! Tôi là Trợ lý AI. Tôi có thể giúp gì cho bạn hôm nay?", getCurrentTime(), false, false, false, false));
                        }
                        sentFileList.addAll(files);
                        sentLinkList.addAll(links);
                    });
                }
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.ERROR, "Lỗi khi tải lịch sử tin nhắn", e);
                Platform.runLater(() -> errorMessage.set("Không thể tải lịch sử tin nhắn: " + e.getMessage()));
            }
        });
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
            SendMessageRequest request = createTextMessage(conversationId, currentUserId, text);
            realtimeChatService.sendMessage(request)
                    .thenRun(() -> {
                        if (isAiChat) {
                            sendAiResponse(conversationId, text);
                        }
                    })
                    .exceptionally(error -> {
                        Platform.runLater(() -> errorMessage.set(
                                "Không thể gửi tin nhắn qua WebSocket: " + rootMessage(error)));
                        return null;
                    });
        }

        if (hasFile) {
            if (isAiChat) {
                errorMessage.set("Trợ lý AI hiện chưa hỗ trợ nhận file.");
                return;
            }

            CompletableFuture.runAsync(() -> uploadAndSendFile(conversationId, file));
        }
    }

    private void handleRealtimeFriend(FriendResponse friend) {
        if (friend == null || friend.getFriendId() == null) return;
        String displayName = friend.getFriendUsername() != null && !friend.getFriendUsername().isBlank()
                ? friend.getFriendUsername() : "Người dùng " + friend.getFriendId();
        Platform.runLater(() -> addFriendToList(friend, displayName));
    }

    private void addFriendToList(FriendResponse friend, String displayName) {
        if (displayNameToUserId.containsValue(friend.getFriendId())) return;
        UserResponse friendUser = new UserResponse();
        friendUser.setId(friend.getFriendId());
        friendUser.setKeycloakUserId(friend.getFriendId());
        friendUser.setUsername(friend.getFriendUsername());
        friendUser.setFullName(friend.getFriendUsername());
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

        ConversationResponse currentConversation = activeConversation.get();
        boolean isActive = currentConversation != null
                && message.getConversationId().equals(currentConversation.getId());

        if (!isMe) {
            updateMessageStatus(message, isActive ? "SEEN" : "DELIVERED");
        }

        if (!isActive) {
            if (!isMe && (message.getStatus() == null || "SENT".equals(message.getStatus()))) {
                Platform.runLater(() -> {
                    conversation.setUnreadCount(conversation.getUnreadCount() + 1);
                    conversationVersion.set(conversationVersion.get() + 1);
                });
            }
            return;
        }

        MessageItem existing = findMessageItem(message.getId());
        if (existing != null) {
            Platform.runLater(() -> {
                existing.update(message);
                refreshPinnedMessages();
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
            refreshPinnedMessages();
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

    private void uploadAndSendFile(Long conversationId, File file) {
        try {
            LOGGER.log(System.Logger.Level.INFO, "Bắt đầu upload file: {0}, size={1}",
                    file.getName(), file.length());
            String uploadedFileUrl = chatService.uploadFile(file, token);

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
        } catch (Exception error) {
            LOGGER.log(System.Logger.Level.ERROR, "Lỗi gửi file", error);
            Platform.runLater(() -> errorMessage.set(
                    "Không thể gửi file qua WebSocket: " + rootMessage(error)));
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
        try {
            AddFriendRequest request = new AddFriendRequest();
            request.setUserId(currentUserId);
            request.setUsername(username.trim());

            FriendResponse friend = chatService.addFriend(request, token);
            if (friend == null || friend.getFriendId() == null) {
                errorMessage.set("Không thể thêm bạn. Vui lòng thử lại.");
                return;
            }

            String displayName = friend.getFriendUsername() != null && !friend.getFriendUsername().isBlank()
                    ? friend.getFriendUsername() : "Người dùng " + friend.getFriendId();

            Platform.runLater(() -> {
                addFriendToList(friend, displayName);
            });

            notificationMessage.set("Đã thêm bạn: " + displayName);
        } catch (Exception e) {
            errorMessage.set("Không thể thêm bạn: " + e.getMessage());
        }
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
        CompletableFuture.runAsync(() -> {
            try {
                chatService.setMessagePinned(IdUtils.parseLongId(item.getResponse().getId()),
                        !item.isPinned(), token);
            } catch (Exception e) {
                Platform.runLater(() -> errorMessage.set("Không thể ghim tin nhắn: " + e.getMessage()));
            }
        });
    }

    public String searchConversationMessages(String query) {
        ConversationResponse conversation = activeConversation.get();
        if (conversation == null || query == null || query.isBlank()) return null;
        try {
            MessageResponse[] results = chatService.searchMessages(
                    IdUtils.parseLongId(conversation.getId()), query, token);
            if (results != null && results.length > 0) {
                return results[0].getContent();
            }
            notificationMessage.set("Không tìm thấy tin nhắn phù hợp.");
        } catch (Exception e) {
            errorMessage.set("Không thể tìm kiếm tin nhắn: " + e.getMessage());
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

    private void refreshPinnedMessages() {
        pinnedMessageList.clear();
        for (MessageItem item : messages) {
            if (item.isPinned() && !item.isDeleted() && !item.isDeletedForMe()) {
                pinnedMessageList.add(pinnedSummary(item.getContent()));
            }
        }
    }

    private String pinnedSummary(String content) {
        String normalized = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        return normalized.length() > 55 ? normalized.substring(0, 55) + "..." : normalized;
    }

    public byte[] downloadFile(MessageResponse msg) throws Exception {
        return chatService.downloadMessageFile(IdUtils.parseLongId(msg.getId()), token);
    }

    public void performSearch(String keyword) {
        Platform.runLater(() -> {
            privateChatList.clear();
            for (String key : nameToUserMap.keySet()) {
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
    public ObservableList<String> getPinnedMessageList() { return pinnedMessageList; }

    public StringProperty currentChatNameProperty() { return currentChatName; }
    public BooleanProperty currentChatIsGroupProperty() { return currentChatIsGroup; }
    public ObjectProperty<ConversationResponse> activeConversationProperty() { return activeConversation; }
    public BooleanProperty isLoadingProperty() { return isLoading; }
    public StringProperty errorMessageProperty() { return errorMessage; }
    public StringProperty notificationMessageProperty() { return notificationMessage; }
    public StringProperty typingTextProperty() { return typingText; }
    public BooleanProperty aiLoadingProperty() { return aiLoading; }
    public IntegerProperty conversationVersionProperty() { return conversationVersion; }

    public String getCurrentUserId() { return currentUserId; }
    public String getToken() { return token; }

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
    
    public GroupResponse getGroupByName(String groupName) {
        return nameToGroupMap.get(groupName);
    }

    public boolean isGroupCreator(String groupName) {
        GroupResponse g = nameToGroupMap.get(groupName);
        return g != null && currentUserId.equals(g.getCreatorId());
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
        public StringProperty statusProperty() { return status; }
        public boolean isStarred() { return starred.get(); }
        public BooleanProperty starredProperty() { return starred; }
        public boolean isPinned() { return pinned.get(); }
        public BooleanProperty pinnedProperty() { return pinned; }

        public void update(MessageResponse updated) {
            response.setContent(updated.getContent());
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
}
