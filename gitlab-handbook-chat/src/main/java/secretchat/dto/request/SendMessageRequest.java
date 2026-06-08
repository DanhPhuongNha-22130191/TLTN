package secretchat.dto.request;

public class SendMessageRequest {
    private Long conversationId;
    private String senderId;
    private String content;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String messageType;
    private Long replyToId;

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }

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

    public Long getReplyToId() { return replyToId; }
    public void setReplyToId(Long replyToId) { this.replyToId = replyToId; }
}
