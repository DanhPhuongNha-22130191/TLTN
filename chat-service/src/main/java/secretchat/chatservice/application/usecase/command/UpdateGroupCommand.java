package secretchat.chatservice.application.usecase.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGroupCommand {
    private String name;
    private String description;
    private String avatarUrl;
}
