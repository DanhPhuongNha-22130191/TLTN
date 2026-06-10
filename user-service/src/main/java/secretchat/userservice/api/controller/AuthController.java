package secretchat.userservice.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import secretchat.userservice.api.dto.*;
import secretchat.userservice.application.dto.ForgotPasswordCommand;
import secretchat.userservice.application.dto.LoginCommand;
import secretchat.userservice.application.dto.LogoutCommand;
import secretchat.userservice.application.dto.RegisterCommand;
import secretchat.userservice.application.usecase.AuthUseCase;

@RestController
@RequestMapping("/api/users/auth")
public class AuthController {
    private static final String PASSWORD_RESET_MESSAGE =
            "Nếu email tồn tại, hướng dẫn đặt lại mật khẩu đã được gửi.";

    private final AuthUseCase authUseCase;

    public AuthController(AuthUseCase authUseCase) {
        this.authUseCase = authUseCase;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = RegisterResponse.from(authUseCase.register(
                new RegisterCommand(request.username(), request.password(),
                        request.confirmPassword(), request.fullName(), request.phoneNumber())
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        var result = authUseCase.login(new LoginCommand(request.username(), request.password()));
        return ResponseEntity.ok(new LoginResponse(
                result.success(), result.message(),
                result.accessToken(), result.refreshToken(), result.expiresIn()
        ));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authUseCase.forgotPassword(new ForgotPasswordCommand(request.email()));
        return ResponseEntity.ok(new ForgotPasswordResponse(PASSWORD_RESET_MESSAGE));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        var result = authUseCase.refreshToken(new secretchat.userservice.application.dto.RefreshTokenCommand(request.refreshToken()));
        return ResponseEntity.ok(new LoginResponse(
                result.success(), result.message(),
                result.accessToken(), result.refreshToken(), result.expiresIn()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authUseCase.logout(new LogoutCommand(request.refreshToken()));
        return ResponseEntity.noContent().build();
    }
}
