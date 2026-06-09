package secretchat.userservice.application.service;

import org.springframework.stereotype.Service;
import secretchat.userservice.application.dto.ChangeRoleCommand;
import secretchat.userservice.application.dto.CreateUserCommand;
import secretchat.userservice.application.dto.UpdateUserCommand;
import secretchat.userservice.application.dto.UserResult;
import secretchat.userservice.application.port.KeycloakUserPort;
import secretchat.userservice.application.usecase.UserUseCase;
import secretchat.userservice.domain.enums.UserStatus;
import secretchat.userservice.domain.exception.UserAlreadyExistsException;
import secretchat.userservice.domain.exception.UserNotFoundException;
import secretchat.userservice.domain.model.User;
import secretchat.userservice.domain.repository.UserRepository;
import secretchat.userservice.domain.valueobject.Email;
import secretchat.userservice.domain.valueobject.FullName;
import secretchat.userservice.domain.valueobject.KeycloakUserId;
import secretchat.userservice.domain.valueobject.PhoneNumber;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class UserApplicationService implements UserUseCase {

    private final UserRepository userRepository;
    private final KeycloakUserPort keycloakUserPort;

    public UserApplicationService(UserRepository userRepository, KeycloakUserPort keycloakUserPort) {
        this.userRepository   = userRepository;
        this.keycloakUserPort = keycloakUserPort;
    }

    @Override
    public UserResult create(CreateUserCommand command) {
        if (userRepository.existsByUsername(command.username())) {
            throw new UserAlreadyExistsException("Username already exists: " + command.username());
        }
        if (userRepository.existsByEmail(new Email(command.email()))) {
            throw new UserAlreadyExistsException("Email already exists: " + command.email());
        }

        String tempPassword = UUID.randomUUID().toString();
        String keycloakId = keycloakUserPort.createUser(
                command.username(), command.email(), tempPassword, command.fullName()
        );

        User user = User.builder()
                .keycloakUserId(new KeycloakUserId(keycloakId))
                .username(command.username())
                .email(new Email(command.email()))
                .fullName(FullName.of(command.fullName()))
                .avatar(command.avatar())
                .phoneNumber(PhoneNumber.of(command.phoneNumber()))
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        return UserResult.from(userRepository.save(user));
    }

    @Override
    public UserResult getById(String keycloakUserId) {
        return userRepository.findByKeycloakUserId(new KeycloakUserId(keycloakUserId))
                .map(UserResult::from)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + keycloakUserId));
    }

    @Override
    public UserResult getByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(UserResult::from)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
    }

    @Override
    public UserResult getByEmail(String email) {
        return userRepository.findByEmail(new Email(email))
                .map(UserResult::from)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + email));
    }

    @Override
    public List<UserResult> getAll() {
        return userRepository.findAll().stream()
                .map(UserResult::from)
                .toList();
    }

    @Override
    public UserResult update(UpdateUserCommand command) {
        User existing = userRepository.findByKeycloakUserId(new KeycloakUserId(command.keycloakUserId()))
                .orElseThrow(() -> new UserNotFoundException("User not found: " + command.keycloakUserId()));
        String username = command.username() == null || command.username().isBlank()
                ? existing.getUsername() : command.username().trim();
        if (!username.equals(existing.getUsername()) && userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("Username already exists: " + username);
        }

        User updated = User.builder()
                .keycloakUserId(existing.getKeycloakUserId())
                .username(username)
                .email(existing.getEmail())
                .fullName(command.fullName() != null ? FullName.of(command.fullName()) : existing.getFullName())
                .avatar(command.avatar() != null ? command.avatar() : existing.getAvatar())
                .phoneNumber(command.phoneNumber() != null ? PhoneNumber.of(command.phoneNumber()) : existing.getPhoneNumber())
                .status(existing.getStatus())
                .createdAt(existing.getCreatedAt())
                .build();

        keycloakUserPort.updateUser(command.keycloakUserId(), username,
                command.fullName(), command.newPassword());
        return UserResult.from(userRepository.save(updated));
    }

    @Override
    public void delete(String keycloakUserId) {
        keycloakUserPort.deleteUser(keycloakUserId);
        userRepository.deleteByKeycloakUserId(new KeycloakUserId(keycloakUserId));
    }

    @Override
    public void changeRole(ChangeRoleCommand command) {
        if (!userRepository.findByKeycloakUserId(new KeycloakUserId(command.keycloakUserId())).isPresent()) {
            throw new UserNotFoundException("User not found: " + command.keycloakUserId());
        }
        keycloakUserPort.assignRole(command.keycloakUserId(), command.role().name());
    }
}
