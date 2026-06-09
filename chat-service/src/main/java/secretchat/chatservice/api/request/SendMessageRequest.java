package secretchat.chatservice.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import secretchat.chatservice.domain.enums.MessageType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

    @NotNull(message = "Conversation ID is required")
    private Long conversationId;

    @NotBlank(message = "Sender ID is required")
    private String senderId;

    private String content;
    private String fileUrl;
    private String fileName;
    
    @jakarta.validation.constraints.Max(value = 104857600, message = "Kích thước file không được vượt quá 100 MB")
    private Long fileSize;
    
    private String fileType;
    private MessageType messageType;
    private Long replyToId;
}
