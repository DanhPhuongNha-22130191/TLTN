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
public class AddFriendRequest {

    @NotNull(message = "User ID is required")
    private String userId;

    @NotBlank(message = "Friend username is required")
    private String username;
}
