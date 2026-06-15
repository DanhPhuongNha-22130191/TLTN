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
    
    @jakarta.validation.constraints.Max(
            value = 52428800,
            message = "File có dung lượng vượt quá 50 MB. Vui lòng chọn file nhỏ hơn để gửi.")
    private Long fileSize;
    
    private String fileType;
    private MessageType messageType;
    private Long replyToId;
}
