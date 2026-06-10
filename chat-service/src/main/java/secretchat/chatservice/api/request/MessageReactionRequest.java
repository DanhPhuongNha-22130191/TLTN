package secretchat.chatservice.api.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MessageReactionRequest {
    @NotBlank
    private String userId;
    private String emoji;
}
