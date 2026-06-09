package secretchat.chatservice.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserProfileRequest {

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotBlank(message = "Username is required")
    private String username;

    private String externalSub;
    private String email;
}
