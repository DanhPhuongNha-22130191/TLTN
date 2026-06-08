package secretchat.common.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Client-side global exception handler that mirrors the BE
 * GlobalExceptionHandler.
 *
 * The BE returns error bodies in this shape:
 * {
 * "timestamp": "...",
 * "status": 400,
 * "error": "Bad Request",
 * "message": "Username already exists"
 * }
 *
 * ApiClient throws a RuntimeException whose message contains "API error: Status
 * code X, body: {...}"
 * when the HTTP response is 4xx / 5xx. This handler parses that raw exception
 * and converts it
 * into a strongly-typed {@link ApiException}.
 */
public class GlobalExceptionHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GlobalExceptionHandler() {
    }

    /**
     * Convert any raw exception coming from {@code ApiClient} into an
     * {@link ApiException}.
     * Call this inside every service method's catch block before re-throwing.
     */
    public static ApiException handle(Exception raw) {
        if (raw instanceof ApiException) {
            return (ApiException) raw;
        }

        String msg = raw.getMessage();
        if (msg != null && msg.startsWith("API error: Status code ")) {
            return parseApiError(msg);
        }

        // Network / connectivity issues
        if (raw instanceof java.net.ConnectException
                || raw instanceof java.net.SocketTimeoutException
                || raw instanceof java.net.UnknownHostException) {
            return new ApiException(0, "Network Error",
                    "Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng.");
        }

        return new ApiException(0, "Unknown Error", "Đã xảy ra lỗi không xác định: " + msg);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private static ApiException parseApiError(String rawMessage) {
        // Format from ApiClient: "API error: Status code 400, body: { ... }"
        int bodyStart = rawMessage.indexOf(", body: ");
        int statusCode = 0;

        try {
            // Extract numeric status
            String statusPart = rawMessage.substring("API error: Status code ".length());
            String statusStr = bodyStart > 0 ? statusPart.substring(0, statusPart.indexOf(",")) : statusPart;
            statusCode = Integer.parseInt(statusStr.trim());
        } catch (NumberFormatException ignored) {
        }

        String body = bodyStart > 0 ? rawMessage.substring(bodyStart + ", body: ".length()) : "";
        return parseBody(statusCode, body);
    }

    private static ApiException parseBody(int statusCode, String body) {
        try {
            JsonNode root = MAPPER.readTree(body);
            String error = root.path("error").asText("Error");
            String message = root.path("message").asText("");

            if (message.isEmpty()) {
                message = root.path("error_description").asText("");
            }

            return new ApiException(statusCode, error, toVietnamese(statusCode, message));
        } catch (Exception e) {
            return toFallback(statusCode);
        }
    }

    /**
     * Map BE / Keycloak error messages to Vietnamese UI messages.
     *
     * Keycloak error_description examples: "Invalid user credentials", "invalid_grant",
     *   "Account is not fully set up", "Account disabled"
     * Spring Security examples: "Bad credentials", "User is disabled", "Account is locked"
     */
    private static String toVietnamese(int statusCode, String beMessage) {
        if (beMessage == null || beMessage.isBlank()) {
            return toFallback(statusCode).getUserMessage();
        }

        String lower = beMessage.toLowerCase();

        // ── Credential / login errors (Keycloak + Spring Security) ───────────
        // Catches: "Invalid user credentials", "invalid credentials", "bad credentials",
        //          "invalid_grant", "invalid password"
        if (lower.contains("invalid") && (lower.contains("credential")
                || lower.contains("grant") || lower.contains("password"))) {
            return "Sai tên đăng nhập hoặc mật khẩu.";
        }
        if (lower.contains("bad credentials") || lower.equals("invalid_grant")) {
            return "Sai tên đăng nhập hoặc mật khẩu.";
        }

        // ── Registration / duplication errors ────────────────────────────────
        if (lower.contains("username already") || lower.contains("user already exists")) {
            return "Tên đăng nhập đã tồn tại.";
        }
        if (lower.contains("already exists") || lower.contains("already taken")) {
            return "Dữ liệu đã tồn tại hoặc tên này đã được sử dụng.";
        }
        if (lower.contains("email already") || lower.contains("email is already")) {
            return "Email đã được sử dụng.";
        }

        // ── Account state errors ─────────────────────────────────────────────
        if (lower.contains("account is disabled") || lower.contains("user is disabled")
                || lower.contains("account disabled") || lower.contains("user disabled")) {
            return "Tài khoản đã bị vô hiệu hoá.";
        }
        if (lower.contains("account is locked") || lower.contains("user is locked")) {
            return "Tài khoản đã bị khoá.";
        }
        if (lower.contains("not fully set up") || lower.contains("email verified")) {
            return "Tài khoản chưa được xác thực. Vui lòng kiểm tra email.";
        }

        // ── Token / session errors ───────────────────────────────────────────
        if (lower.contains("jwt") || lower.contains("token expired")
                || lower.contains("token is not valid")) {
            return "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.";
        }

        // ── Any 401 without a recognised message = wrong credentials ─────────
        if (statusCode == 401) {
            return "Sai tên đăng nhập hoặc mật khẩu.";
        }

        // ── Fallback: show raw BE message if it is short ─────────────────────
        if (beMessage.length() < 120) return beMessage;
        return toFallback(statusCode).getUserMessage();
    }

    private static ApiException toFallback(int statusCode) {
        return switch (statusCode) {
            case 400 -> ApiException.badRequest("Dữ liệu gửi lên không hợp lệ.");
            case 401 -> new ApiException(401, "Unauthorized", "Sai tên đăng nhập hoặc mật khẩu.");
            case 403 -> ApiException.forbidden();
            case 404 -> ApiException.notFound("Tài nguyên");
            case 409 -> ApiException.conflict("Dữ liệu đã tồn tại.");
            case 422 -> ApiException.unprocessable("Dữ liệu không thể xử lý.");
            case 500 -> ApiException.serverError();
            default  -> new ApiException(statusCode, "Error", "Đã xảy ra lỗi. Vui lòng thử lại.");
        };
    }
}

