package secretchat.chat.service;

import secretchat.common.exception.GlobalExceptionHandler;
import secretchat.dto.request.*;
import secretchat.dto.response.*;
import secretchat.service.ApiClient;

import java.util.Arrays;
import java.util.List;

public class ChatService {

    private final ApiClient apiClient;

    public ChatService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    // --- User Service Calls ---

    public UserResponse[] getAllUsers(String token) throws Exception {
        try {
            return apiClient.get("/api/users", token, UserResponse[].class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public UserResponse getUserByUsername(String username, String token) throws Exception {
        try {
            return apiClient.get("/api/user/username/" + username, token, UserResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public FriendResponse addFriend(AddFriendRequest request, String token) throws Exception {
        try {
            return apiClient.post("/api/friends", request, token, FriendResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public FriendResponse[] getFriends(String userId, String token) throws Exception {
        try {
            return apiClient.get("/api/friends/user/" + userId, token, FriendResponse[].class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public UserProfileResponse getUserProfileByExternalSub(String externalSub, String token) throws Exception {
        try {
            return apiClient.get("/api/user/external/" + externalSub, token, UserProfileResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public UserProfileResponse getUserProfileById(String userId, String token) throws Exception {
        try {
            return apiClient.get("/api/user/profile/user/" + userId, token, UserProfileResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }
    // --- Conversations Service Calls ---

    public ConversationResponse createPersonalConversation(CreatePersonalConversationRequest request, String token)
            throws Exception {
        try {
            return apiClient.post("/api/conversations/personal", request, token, ConversationResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public ConversationResponse createGroupConversation(Long groupId, String token) throws Exception {
        try {
            return apiClient.post("/api/conversations/group/" + groupId, null, token, ConversationResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public ConversationResponse getConversationDetails(Long id, String token) throws Exception {
        try {
            return apiClient.get("/api/conversations/" + id, token, ConversationResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public ConversationResponse[] getUserConversations(String userId, String token) throws Exception {
        try {
            return apiClient.get("/api/conversations/user/" + userId, token, ConversationResponse[].class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public ConversationResponse markConversationAsRead(Long id, String token) throws Exception {
        try {
            return apiClient.put("/api/conversations/" + id + "/read", token, ConversationResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public void deleteConversation(Long id, String token) throws Exception {
        try {
            apiClient.delete("/api/conversations/" + id, token);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    // --- Messages Service Calls ---

    public String uploadFile(java.io.File file, String token) throws Exception {
        return apiClient.uploadFile("/api/messages/upload", file, token);
    }

    public MessageResponse sendMessage(SendMessageRequest request, String token) throws Exception {
        try {
            return apiClient.post("/api/messages", request, token, MessageResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public MessageResponse[] getConversationChatHistory(Long conversationId, String token) throws Exception {
        try {
            return apiClient.get("/api/messages/history/" + conversationId, token, MessageResponse[].class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public MessageResponse recallMessage(Long messageId, String userId, String token) throws Exception {
        try {
            return apiClient.put("/api/messages/" + messageId + "/recall?userId=" + userId, token,
                    MessageResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public void deleteMessageForUser(Long messageId, String userId, String token) throws Exception {
        try {
            apiClient.delete("/api/messages/" + messageId + "?userId=" + userId, token);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public void removeFriend(String userId, String friendId, String token) throws Exception {
        try {
            apiClient.delete("/api/friends/user/"
                    + java.net.URLEncoder.encode(userId, java.nio.charset.StandardCharsets.UTF_8) + "/"
                    + java.net.URLEncoder.encode(friendId, java.nio.charset.StandardCharsets.UTF_8), token);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public MessageResponse editMessage(Long messageId, UpdateMessageRequest request, String token) throws Exception {
        try {
            return apiClient.put("/api/messages/" + messageId, request, token, MessageResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public MessageResponse setMessageStarred(Long messageId, boolean value, String token) throws Exception {
        try {
            return apiClient.put("/api/messages/" + messageId + "/star?value=" + value,
                    token, MessageResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public MessageResponse setMessagePinned(Long messageId, boolean value, String token) throws Exception {
        try {
            return apiClient.put("/api/messages/" + messageId + "/pin?value=" + value,
                    token, MessageResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public MessageResponse updateMessageStatus(
            Long messageId, MessageStatusRequest request, String token) throws Exception {
        try {
            return apiClient.put("/api/messages/" + messageId + "/status",
                    request, token, MessageResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public MessageResponse[] getPinnedMessages(Long conversationId, String token) throws Exception {
        try {
            return apiClient.get("/api/messages/pinned/" + conversationId, token, MessageResponse[].class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public MessageResponse[] searchMessages(Long conversationId, String query, String token) throws Exception {
        try {
            return apiClient.get("/api/messages/search/" + conversationId + "?query="
                    + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8),
                    token, MessageResponse[].class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public byte[] downloadMessageFile(Long messageId, String token) throws Exception {
        try {
            return apiClient.getBytes("/api/messages/" + messageId + "/download", token);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    // --- Groups Service Calls ---

    public GroupResponse createGroup(CreateGroupRequest request, String token) throws Exception {
        try {
            return apiClient.post("/api/groups", request, token, GroupResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public GroupResponse updateGroupDetails(Long id, UpdateGroupRequest request, String token) throws Exception {
        try {
            return apiClient.put("/api/groups/" + id, request, token, GroupResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public GroupResponse getGroupDetails(Long id, String token) throws Exception {
        try {
            return apiClient.get("/api/groups/" + id, token, GroupResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public GroupResponse addGroupMember(Long groupId, AddGroupMemberRequest request, String token) throws Exception {
        try {
            return apiClient.post("/api/groups/" + groupId + "/members", request, token, GroupResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public GroupMemberResponse updateMemberNickname(Long groupId, String userId, UpdateMemberNicknameRequest request,
            String token) throws Exception {
        try {
            return apiClient.put("/api/groups/" + groupId + "/members/" + userId + "/nickname", request, token,
                    GroupMemberResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public GroupMemberResponse updateMemberRole(Long groupId, String userId, UpdateMemberRoleRequest request,
            String token) throws Exception {
        try {
            return apiClient.put("/api/groups/" + groupId + "/members/" + userId + "/role", request, token,
                    GroupMemberResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public GroupMemberResponse[] getGroupMembersList(Long groupId, String token) throws Exception {
        try {
            return apiClient.get("/api/groups/" + groupId + "/members", token, GroupMemberResponse[].class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public void removeGroupMember(Long groupId, String userId, String token) throws Exception {
        try {
            apiClient.delete("/api/groups/" + groupId + "/members/" + userId, token);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public void deleteGroup(Long groupId, String userId, String token) throws Exception {
        try {
            apiClient.delete("/api/groups/" + groupId + "?userId="
                    + java.net.URLEncoder.encode(userId, java.nio.charset.StandardCharsets.UTF_8), token);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }

    public GroupResponse transferGroupOwnership(Long groupId, String currentOwnerId, String newOwnerId,
            String token) throws Exception {
        String query = "?currentOwnerId=" + java.net.URLEncoder.encode(currentOwnerId, java.nio.charset.StandardCharsets.UTF_8)
                + "&newOwnerId=" + java.net.URLEncoder.encode(newOwnerId, java.nio.charset.StandardCharsets.UTF_8);
        try {
            return apiClient.put("/api/groups/" + groupId + "/owner" + query, token, GroupResponse.class);
        } catch (Exception e) {
            throw GlobalExceptionHandler.handle(e);
        }
    }
}
