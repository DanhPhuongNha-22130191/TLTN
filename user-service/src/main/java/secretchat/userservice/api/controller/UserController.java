package secretchat.userservice.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import secretchat.userservice.api.dto.*;
import secretchat.userservice.application.dto.ChangeRoleCommand;
import secretchat.userservice.application.dto.CreateUserCommand;
import secretchat.userservice.application.dto.UpdateUserCommand;
import secretchat.userservice.application.usecase.UserUseCase;
import secretchat.userservice.domain.enums.UserRole;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserUseCase userUseCase;

    public UserController(UserUseCase userUseCase) {
        this.userUseCase = userUseCase;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAll() {
        return ResponseEntity.ok(userUseCase.getAll().stream().map(UserResponse::from).toList());
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(UserResponse.from(userUseCase.getById(jwt.getSubject())));
    }

    @GetMapping("/{keycloakUserId}")
    public ResponseEntity<UserResponse> getById(@PathVariable String keycloakUserId) {
        return ResponseEntity.ok(UserResponse.from(userUseCase.getById(keycloakUserId)));
    }

    @GetMapping("/username/{username}")
    public ResponseEntity<UserResponse> getByUsername(@PathVariable String username) {
        return ResponseEntity.ok(UserResponse.from(userUseCase.getByUsername(username)));
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<UserResponse> getByEmail(@PathVariable String email) {
        return ResponseEntity.ok(UserResponse.from(userUseCase.getByEmail(email)));
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = UserResponse.from(userUseCase.create(
                new CreateUserCommand(request.username(), request.email(),
                        request.fullName(), request.phoneNumber(), request.avatar())
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateCurrentUser(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        if (jwt == null || jwt.getSubject() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String authenticatedUserId = jwt.getSubject();
        UserResponse response = UserResponse.from(userUseCase.update(
                new UpdateUserCommand(authenticatedUserId, request.username(), request.fullName(),
                        request.avatar(), request.phoneNumber(), request.newPassword())
        ));
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{keycloakUserId}")
    public ResponseEntity<UserResponse> update(
            @PathVariable String keycloakUserId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        if (jwt == null || !keycloakUserId.equals(jwt.getSubject())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        UserResponse response = UserResponse.from(userUseCase.update(
                new UpdateUserCommand(keycloakUserId, request.username(), request.fullName(),
                        request.avatar(), request.phoneNumber(), request.newPassword())
        ));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{keycloakUserId}")
    public ResponseEntity<Void> delete(@PathVariable String keycloakUserId) {
        userUseCase.delete(keycloakUserId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{keycloakUserId}/role")
    public ResponseEntity<Void> changeRole(
            @PathVariable String keycloakUserId,
            @Valid @RequestBody ChangeRoleRequest request
    ) {
        userUseCase.changeRole(new ChangeRoleCommand(keycloakUserId, UserRole.valueOf(request.role())));
        return ResponseEntity.noContent().build();
    }
}
