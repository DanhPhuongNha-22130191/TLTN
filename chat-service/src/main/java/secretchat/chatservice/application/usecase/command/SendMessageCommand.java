package secretchat.chatservice.application.usecase.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import secretchat.chatservice.domain.enums.MessageType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageCommand {
    private Long conversationId;
    private String senderId;
    private String content;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private MessageType messageType;
    private Long replyToId;
}
