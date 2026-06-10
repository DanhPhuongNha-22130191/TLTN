package secretchat.userservice.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import secretchat.userservice.domain.exception.InvalidCredentialsException;
import secretchat.userservice.domain.exception.InvalidTokenException;
import secretchat.userservice.domain.exception.KeycloakException;
import secretchat.userservice.domain.exception.MailboxException;
import secretchat.userservice.domain.exception.RoleNotFoundException;
import secretchat.userservice.domain.exception.UserAlreadyExistsException;
import secretchat.userservice.domain.exception.UserNotFoundException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    record ErrorResponse(int status, String error, String message, String path, LocalDateTime timestamp) {}

    // ── Validation ────────────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    // ── Domain: User ──────────────────────────────────────────────────────────

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(
            UserAlreadyExistsException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
    }

    // ── Domain: Auth ──────────────────────────────────────────────────────────

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(
            InvalidTokenException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request.getRequestURI());
    }

    // ── Domain: Role ──────────────────────────────────────────────────────────

    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoleNotFound(
            RoleNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    // ── Infrastructure: Keycloak ──────────────────────────────────────────────

    @ExceptionHandler(KeycloakException.class)
    public ResponseEntity<ErrorResponse> handleKeycloak(
            KeycloakException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MailboxException.class)
    public ResponseEntity<ErrorResponse> handleMailbox(
            MailboxException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), request.getRequestURI());
    }

    // ── Fallback ──────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest request) {
        // Log for server-side debugging while returning the real cause to the client
        String message = ex.getCause() != null
                ? ex.getMessage() + " (caused by: " + ex.getCause().getMessage() + ")"
                : ex.getMessage();
        return build(HttpStatus.INTERNAL_SERVER_ERROR, message, request.getRequestURI());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, String path) {
        return ResponseEntity.status(status).body(
                new ErrorResponse(status.value(), status.getReasonPhrase(), message, path, LocalDateTime.now())
        );
    }
}
