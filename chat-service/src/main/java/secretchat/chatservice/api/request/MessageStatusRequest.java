package secretchat.chatservice.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import secretchat.chatservice.domain.enums.MessageStatus;

@Data
public class MessageStatusRequest {
    @NotBlank
    private String userId;
    @NotNull
    private MessageStatus status;
}
