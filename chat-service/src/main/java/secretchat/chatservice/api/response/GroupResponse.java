package secretchat.chatservice.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupResponse {
    private Long id;
    private String name;
    private String description;
    private String creatorId;
    private String avatarUrl;
    private boolean isActive;
    private List<GroupMemberResponse> members;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
