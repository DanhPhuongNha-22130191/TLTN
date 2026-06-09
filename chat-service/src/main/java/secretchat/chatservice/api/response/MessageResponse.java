package secretchat.chatservice.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import secretchat.chatservice.domain.enums.MessageType;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    private Long id;
    private Long conversationId;
    private String senderId;
    private String content;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private MessageType messageType;
    private Long replyToId;
    private boolean isDeleted;
    private LocalDateTime deletedAt;
    private String deletedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String deletedForUsers;
}
