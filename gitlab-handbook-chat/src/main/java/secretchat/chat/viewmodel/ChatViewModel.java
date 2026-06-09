package secretchat.chat.viewmodel;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import secretchat.chat.service.AIService;
import secretchat.chat.service.ChatService;
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
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatViewModel {

    private static final System.Logger LOGGER = System.getLogger(ChatViewModel.class.getName());

    private final ChatService chatService;
    private final AIService aiService;
    
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

    private final StringProperty currentChatName = new SimpleStringProperty();
    private final BooleanProperty currentChatIsGroup = new SimpleBooleanProperty(false);
    private final ObjectProperty<ConversationResponse> activeConversation = new SimpleObjectProperty<>();

    private final BooleanProperty isLoading = new SimpleBooleanProperty(false);
    private final StringProperty errorMessage = new SimpleStringProperty();
    private final StringProperty notificationMessage = new SimpleStringProperty();

    public ChatViewModel() {
        this.chatService = new ChatService(ApiClient.getInstance());
        this.aiService = new AIService();
    }

    public void init() {
        token = SessionManager.getInstance().getAccessToken();
        if (token == null || token.isBlank()) {
            errorMessage.set("Không tìm thấy token đăng nhập. Vui lòng đăng nhập lại.");
            return;
        }

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

                        if (!displayNameToUserId.containsKey(displayName)) {
                            UserResponse friendUser = new UserResponse();
                            friendUser.setUsername(friend.getFriendUsername());
                            friendUser.setFullName(friend.getFriendUsername());
                            nameToUserMap.put(displayName, friendUser);
                            displayNameToUserId.put(displayName, friend.getFriendId());
                            privateChatList.add(displayName);
                        }
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
        loadChatInfo(selectedUserStr, false);
        loadMessages(conv.getId(), 0); // Ignore unreadCount for loading as it's just marked 0
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
        loadGroupChatInfo(selectedGroup);
        loadMessages(conv.getId(), 0);
    }

    private void loadChatInfo(String chatName, boolean isGroup) {
        Platform.runLater(() -> {
            memberList.clear();
            sentFileList.clear();
            sentLinkList.clear();

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
        Platform.runLater(messages::clear);

        CompletableFuture.runAsync(() -> {
            try {
                MessageResponse[] history = chatService.getConversationChatHistory(IdUtils.parseLongId(conversationId), token);
                if (history != null) {
                    List<MessageItem> newMessages = new ArrayList<>();
                    List<String> files = new ArrayList<>();
                    List<String> links = new ArrayList<>();

                    for (MessageResponse msg : history) {
                        boolean isMe = msg.getSenderId() != null && msg.getSenderId().equals(currentUserId);
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

        try {
            boolean isAiChat = "TRỢ LÝ AI".equals(currentChatName.get());

            if (hasText) {
                SendMessageRequest req = new SendMessageRequest();
                req.setConversationId(IdUtils.parseLongId(activeConversation.get().getId()));
                req.setSenderId(currentUserId);
                req.setContent(text);
                req.setMessageType("TEXT");

                MessageResponse response = chatService.sendMessage(req, token);
                MessageItem item = new MessageItem(response, "Bạn", text, getCurrentTime(), true, false, false, false);
                messages.add(item);
                extractAndAddLinks(text);

                if (isAiChat) {
                    CompletableFuture.runAsync(() -> {
                        String aiResponseText = aiService.callAIAssistant(text);
                        try {
                            SendMessageRequest aiReq = new SendMessageRequest();
                            aiReq.setConversationId(IdUtils.parseLongId(activeConversation.get().getId()));
                            aiReq.setSenderId("AI_ASSISTANT");
                            aiReq.setContent(aiResponseText);
                            aiReq.setMessageType("TEXT");

                            MessageResponse aiResponse = chatService.sendMessage(aiReq, token);
                            Platform.runLater(() -> messages.add(new MessageItem(aiResponse, "TRỢ LÝ AI", aiResponseText, getCurrentTime(), false, false, false, false)));
                        } catch (Exception ex) {
                            Platform.runLater(() -> errorMessage.set("Không thể lưu phản hồi của AI: " + ex.getMessage()));
                        }
                    });
                }
            }

            if (hasFile) {
                if (isAiChat) {
                    errorMessage.set("Trợ lý AI hiện chưa hỗ trợ nhận file.");
                    return;
                }

                CompletableFuture.runAsync(() -> {
                    try {
                        LOGGER.log(System.Logger.Level.INFO, "Bắt đầu upload file: {0}, size={1}", file.getName(), file.length());
                        String uploadedFileUrl = chatService.uploadFile(file, token);
                        LOGGER.log(System.Logger.Level.INFO, "Upload thành công, fileUrl={0}", uploadedFileUrl);

                        SendMessageRequest req = new SendMessageRequest();
                        req.setConversationId(IdUtils.parseLongId(activeConversation.get().getId()));
                        req.setSenderId(currentUserId);
                        req.setFileName(file.getName());
                        req.setFileSize(file.length());
                        req.setFileUrl(uploadedFileUrl);
                        req.setFileType(FileUtils.getFileExtension(file));

                        String ext = FileUtils.getFileExtension(file).toLowerCase();
                        if (ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg") || ext.equals("gif")) {
                            req.setMessageType("IMAGE");
                        } else {
                            req.setMessageType("FILE");
                        }

                        LOGGER.log(System.Logger.Level.INFO, "Gửi tin nhắn file tới API, messageType={0}", req.getMessageType());
                        MessageResponse response = chatService.sendMessage(req, token);
                        MessageItem item = new MessageItem(response, "Bạn", file.getName(), getCurrentTime(), true, true, false, false);
                        
                        Platform.runLater(() -> {
                            messages.add(item);
                            sentFileList.add(file.getName());
                        });
                    } catch (Exception ex) {
                        LOGGER.log(System.Logger.Level.ERROR, "Lỗi gửi file", ex);
                        Platform.runLater(() -> errorMessage.set("Không thể gửi file: " + ex.getMessage()));
                    }
                });
            }

        } catch (Exception e) {
            errorMessage.set("Không thể gửi tin nhắn: " + e.getMessage());
        }
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
                if (!displayNameToUserId.containsKey(displayName)) {
                    UserResponse friendUser = new UserResponse();
                    friendUser.setId(friend.getFriendId());
                    friendUser.setKeycloakUserId(friend.getFriendId());
                    friendUser.setUsername(friend.getFriendUsername());
                    friendUser.setFullName(friend.getFriendUsername());
                    nameToUserMap.put(displayName, friendUser);
                    displayNameToUserId.put(displayName, friend.getFriendId());
                    privateChatList.add(displayName);
                }
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

    public StringProperty currentChatNameProperty() { return currentChatName; }
    public BooleanProperty currentChatIsGroupProperty() { return currentChatIsGroup; }
    public ObjectProperty<ConversationResponse> activeConversationProperty() { return activeConversation; }
    public BooleanProperty isLoadingProperty() { return isLoading; }
    public StringProperty errorMessageProperty() { return errorMessage; }
    public StringProperty notificationMessageProperty() { return notificationMessage; }

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
        private final String content;
        private final String time;
        private final boolean isMe;
        private final boolean isFile;
        private final BooleanProperty isDeleted = new SimpleBooleanProperty();
        private final BooleanProperty isDeletedForMe = new SimpleBooleanProperty();

        public MessageItem(MessageResponse response, String senderName, String content, String time, boolean isMe, boolean isFile, boolean isDeleted, boolean isDeletedForMe) {
            this.response = response;
            this.senderName = senderName;
            this.content = content;
            this.time = time;
            this.isMe = isMe;
            this.isFile = isFile;
            this.isDeleted.set(isDeleted);
            this.isDeletedForMe.set(isDeletedForMe);
        }

        public MessageResponse getResponse() { return response; }
        public String getSenderName() { return senderName; }
        public String getContent() { return content; }
        public String getTime() { return time; }
        public boolean isMe() { return isMe; }
        public boolean isFile() { return isFile; }
        
        public boolean isDeleted() { return isDeleted.get(); }
        public void setDeleted(boolean value) { this.isDeleted.set(value); }
        public BooleanProperty isDeletedProperty() { return isDeleted; }

        public boolean isDeletedForMe() { return isDeletedForMe.get(); }
        public void setDeletedForMe(boolean value) { this.isDeletedForMe.set(value); }
        public BooleanProperty isDeletedForMeProperty() { return isDeletedForMe; }
    }
}
