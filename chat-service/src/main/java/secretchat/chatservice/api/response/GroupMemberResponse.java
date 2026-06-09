package secretchat.chatservice.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberResponse {
    private Long groupId;
    private String userId;
    private String role;
    private String nickname;
    private String invitedBy;
    private LocalDateTime joinedAt;
}
