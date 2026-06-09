package secretchat.userservice.application.usecase;

import secretchat.userservice.application.dto.ChangeRoleCommand;
import secretchat.userservice.application.dto.CreateUserCommand;
import secretchat.userservice.application.dto.UpdateUserCommand;
import secretchat.userservice.application.dto.UserResult;

import java.util.List;

public interface UserUseCase {
    UserResult create(CreateUserCommand command);
    UserResult getById(String keycloakUserId);
    UserResult getByUsername(String username);
    UserResult getByEmail(String email);
    List<UserResult> getAll();
    UserResult update(UpdateUserCommand command);
    void delete(String keycloakUserId);
    void changeRole(ChangeRoleCommand command);
}
