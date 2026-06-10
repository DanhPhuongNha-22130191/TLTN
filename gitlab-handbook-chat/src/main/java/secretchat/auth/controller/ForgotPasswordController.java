package secretchat.auth.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import secretchat.auth.dto.response.ForgotPasswordResponse;
import secretchat.auth.service.ForgotPasswordService;
import secretchat.common.exception.ApiException;
import secretchat.service.ApiClient;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class ForgotPasswordController {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE);
    private static final String DEFAULT_SUCCESS_MESSAGE =
            "Nếu email tồn tại, hướng dẫn đặt lại mật khẩu đã được gửi.";

    @FXML private TextField emailField;
    @FXML private Label emailError;
    @FXML private Label resultLabel;
    @FXML private Button sendButton;
    @FXML private Button cancelButton;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private FontIcon resetIcon;
    @FXML private Label fallbackIconLabel;

    private final ForgotPasswordService service =
            new ForgotPasswordService(ApiClient.getInstance());

    @FXML
    private void initialize() {
        ensureResetIcon();
        emailField.textProperty().addListener((observable, oldValue, newValue) -> {
            emailError.setVisible(false);
            emailError.setManaged(false);
        });
    }

    @FXML
    private void handleSend() {
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            showEmailError(email.isEmpty()
                    ? "Vui lòng nhập email."
                    : "Email không đúng định dạng.");
            return;
        }

        setLoading(true);
        resultLabel.setVisible(false);
        resultLabel.setManaged(false);
        CompletableFuture.supplyAsync(() -> requestReset(email))
                .thenAccept(response -> Platform.runLater(() -> showSuccess(response)))
                .exceptionally(error -> {
                    Platform.runLater(() -> showError(error));
                    return null;
                });
    }

    private ForgotPasswordResponse requestReset(String email) {
        try {
            return service.requestReset(email);
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    private void showSuccess(ForgotPasswordResponse response) {
        setLoading(false);
        String message = response == null || response.getMessage() == null
                || response.getMessage().isBlank()
                ? DEFAULT_SUCCESS_MESSAGE : response.getMessage();
        resultLabel.setText(message);
        resultLabel.setStyle("-fx-text-fill: #16815d;");
        resultLabel.setVisible(true);
        resultLabel.setManaged(true);
        sendButton.setDisable(true);
    }

    private void showError(Throwable error) {
        setLoading(false);
        Throwable cause = error.getCause() == null ? error : error.getCause();
        if (cause instanceof RuntimeException runtime && runtime.getCause() != null) {
            cause = runtime.getCause();
        }
        String message = cause instanceof ApiException api
                ? api.getUserMessage()
                : "Không thể gửi email lúc này. Vui lòng thử lại sau.";
        resultLabel.setText(message);
        resultLabel.setStyle("-fx-text-fill: #c81e4d;");
        resultLabel.setVisible(true);
        resultLabel.setManaged(true);
    }

    private void showEmailError(String message) {
        emailError.setText(message);
        emailError.setVisible(true);
        emailError.setManaged(true);
    }

    private void setLoading(boolean loading) {
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
        sendButton.setDisable(loading);
        cancelButton.setDisable(loading);
        emailField.setDisable(loading);
    }

    private void ensureResetIcon() {
        try {
            resetIcon.setIconLiteral("fa-envelope");
            boolean available = resetIcon.getIconCode() != null;
            resetIcon.setVisible(available);
            resetIcon.setManaged(available);
            fallbackIconLabel.setVisible(!available);
            fallbackIconLabel.setManaged(!available);
        } catch (RuntimeException error) {
            resetIcon.setVisible(false);
            resetIcon.setManaged(false);
            fallbackIconLabel.setVisible(true);
            fallbackIconLabel.setManaged(true);
        }
    }

    @FXML
    private void handleCancel() {
        ((Stage) emailField.getScene().getWindow()).close();
    }
}
