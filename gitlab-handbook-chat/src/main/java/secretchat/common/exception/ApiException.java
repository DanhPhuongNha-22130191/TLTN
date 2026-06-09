package secretchat.common.exception;

/**
 * Typed exception mirroring the BE GlobalExceptionHandler's error shape:
 * {
 *   "status":  400 | 401 | 403 | 404 | 409 | 422 | 500,
 *   "error":   "Bad Request" | "Unauthorized" | ...,
 *   "message": "Human-readable detail"
 * }
 */
public class ApiException extends RuntimeException {

    private final int statusCode;
    private final String error;
    private final String userMessage;

    public ApiException(int statusCode, String error, String userMessage) {
        super("[" + statusCode + "] " + error + ": " + userMessage);
        this.statusCode = statusCode;
        this.error = error;
        this.userMessage = userMessage;
    }

    public int getStatusCode() { return statusCode; }
    public String getError()   { return error; }

    /** Vietnamese-friendly message ready to display in the UI. */
    public String getUserMessage() { return userMessage; }

    // ── Convenience factory methods matching BE HTTP statuses ────────────────

    public static ApiException badRequest(String message) {
        return new ApiException(400, "Bad Request", message);
    }

    public static ApiException unauthorized() {
        return new ApiException(401, "Unauthorized", "Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.");
    }

    public static ApiException forbidden() {
        return new ApiException(403, "Forbidden", "Bạn không có quyền thực hiện thao tác này.");
    }

    public static ApiException notFound(String resource) {
        return new ApiException(404, "Not Found", resource + " không tồn tại.");
    }

    public static ApiException conflict(String message) {
        return new ApiException(409, "Conflict", message);
    }

    public static ApiException unprocessable(String message) {
        return new ApiException(422, "Unprocessable Entity", message);
    }

    public static ApiException serverError() {
        return new ApiException(500, "Internal Server Error", "Lỗi máy chủ. Vui lòng thử lại sau.");
    }
}
