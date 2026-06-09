package secretchat.chatservice.api.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateMessageRequest {
    @NotBlank
    private String userId;
    @NotBlank
    private String content;
}
