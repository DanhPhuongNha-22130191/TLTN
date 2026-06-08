package secretchat.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageResponse {
    private String id;
    private String conversationId;
    private String senderId;
    private String content;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String messageType;
    private String replyToId;
    private boolean isDeleted;
    private String createdAt;
    private String deletedForUsers;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getReplyToId() { return replyToId; }
    public void setReplyToId(String replyToId) { this.replyToId = replyToId; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getDeletedForUsers() { return deletedForUsers; }
    public void setDeletedForUsers(String deletedForUsers) { this.deletedForUsers = deletedForUsers; }
}
