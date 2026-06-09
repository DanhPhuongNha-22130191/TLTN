package secretchat.chatservice.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupRequest {
    @NotBlank(message = "Group name is required")
    private String name;

    private String description;

    @NotBlank(message = "Creator ID is required")
    private String creatorId;

    private String avatarUrl;

    private List<String> memberIds;
}
