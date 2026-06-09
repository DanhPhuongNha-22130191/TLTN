package secretchat.chatservice.application.usecase.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupCommand {
    private String name;
    private String description;
    private String creatorId;
    private String avatarUrl;
    private List<String> memberIds;
}
