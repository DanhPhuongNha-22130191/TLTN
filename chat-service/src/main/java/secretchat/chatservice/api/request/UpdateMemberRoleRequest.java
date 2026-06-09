package secretchat.chatservice.api.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateMemberRoleRequest {
    @NotBlank(message = "Role must not be empty")
    private String role;
}
