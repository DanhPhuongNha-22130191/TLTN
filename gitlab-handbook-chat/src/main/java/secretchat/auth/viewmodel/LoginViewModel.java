package secretchat.auth.viewmodel;

import javafx.application.Platform;
import java.util.concurrent.CompletableFuture;

import secretchat.auth.dto.request.LoginRequest;
import secretchat.auth.dto.response.LoginResponse;
import secretchat.auth.service.LoginService;
import secretchat.common.exception.ApiException;
import secretchat.service.ApiClient;
import secretchat.service.SessionManager;

public class LoginViewModel extends BaseAuthViewModel {

    private static final System.Logger LOGGER = System.getLogger(LoginViewModel.class.getName());
    private final LoginService loginService;

    private final javafx.beans.property.BooleanProperty loginSuccess = new javafx.beans.property.SimpleBooleanProperty(false);

    public LoginViewModel() {
        this(new LoginService(ApiClient.getInstance()));
    }

    public LoginViewModel(LoginService loginService) {
        this.loginService = loginService;
    }

    public javafx.beans.property.BooleanProperty loginSuccessProperty() {
        return loginSuccess;
    }

    @Override
    public boolean validateInput() {
        boolean isValid = true;
        usernameError.set("");
        passwordError.set("");
        globalMessage.set("");

        String usernameVal = username.get().trim();
        if (usernameVal.isEmpty()) {
            usernameError.set("Tên đăng nhập không được để trống");
            isValid = false;
        }

        String passVal = password.get();
        if (passVal.isEmpty()) {
            passwordError.set("Mật khẩu không được để trống");
            isValid = false;
        }

        return isValid;
    }

    public CompletableFuture<Void> loginAsync() {
        if (!validateInput()) {
            return CompletableFuture.completedFuture(null);
        }

        isLoading.set(true);
        loginSuccess.set(false);
        globalMessage.set("Đang đăng nhập...");
        globalMessageStyle.set("-fx-text-fill: #6a8dff;");

        LoginRequest request = new LoginRequest();
        request.setUsername(username.get().trim());
        request.setPassword(password.get());

        return CompletableFuture.runAsync(() -> {
            try {
                LoginResponse response = loginService.login(request);

                LOGGER.log(System.Logger.Level.INFO, "Phản hồi đăng nhập: accessTokenReceived={0}", 
                        (response != null && response.getAccessToken() != null && !response.getAccessToken().isBlank()));

                Platform.runLater(() -> {
                    isLoading.set(false);
                    // BE trả về {accessToken, refreshToken, ...} — KHÔNG có field `success`
                    // → detect thành công bằng sự hiện diện của accessToken
                    boolean hasToken = response != null
                            && response.getAccessToken() != null
                            && !response.getAccessToken().isBlank();

                    if (hasToken) {
                        SessionManager.getInstance().setAccessToken(response.getAccessToken());
                        if (response.getRefreshToken() != null && !response.getRefreshToken().isBlank()) {
                            SessionManager.getInstance().setRefreshToken(response.getRefreshToken());
                        }
                        globalMessage.set("Đăng nhập thành công!");
                        globalMessageStyle.set("-fx-text-fill: #2e7d32;");
                        loginSuccess.set(true);
                    } else {
                        // BE trả về body rỗng / không có token (hiếm khi xảy ra nếu không throw)
                        globalMessage.set(response != null && response.getMessage() != null
                                ? response.getMessage() : "Sai tài khoản hoặc mật khẩu.");
                        globalMessageStyle.set("-fx-text-fill: #ff4d6d;");
                    }
                });
            } catch (ApiException e) {
                // Lỗi từ BE (4xx/5xx) đã được parse thành tiếng Việt bởi GlobalExceptionHandler
                Platform.runLater(() -> {
                    isLoading.set(false);
                    globalMessage.set(e.getUserMessage());
                    globalMessageStyle.set("-fx-text-fill: #ff4d6d;");
                });
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.ERROR, "Lỗi đăng nhập", e);
                Platform.runLater(() -> {
                    isLoading.set(false);
                    globalMessage.set("Không thể kết nối đến máy chủ.");
                    globalMessageStyle.set("-fx-text-fill: #ff4d6d;");
                });
            }
        });
    }
}
