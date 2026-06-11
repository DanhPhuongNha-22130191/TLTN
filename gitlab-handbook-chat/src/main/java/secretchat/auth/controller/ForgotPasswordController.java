package secretchat.auth.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.css.PseudoClass;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import secretchat.auth.dto.response.ForgotPasswordResponse;
import secretchat.auth.service.ForgotPasswordService;
import secretchat.common.exception.ApiException;
import secretchat.service.ApiClient;
import secretchat.util.LinkUtils;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class ForgotPasswordController {
    private static final String MAIL_DOMAIN = "gitlab.handbook.local";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final String DEFAULT_SUCCESS_MESSAGE =
            "Nếu email tồn tại, hướng dẫn đặt lại mật khẩu đã được gửi.";
    private static final PseudoClass FOCUSED = PseudoClass.getPseudoClass("focused");

    @FXML private TextField emailField;
    @FXML private HBox emailInputGroup;
    @FXML private Label emailError;
    @FXML private Label resultLabel;
    @FXML private Hyperlink webmailLink;
    @FXML private Button sendButton;
    @FXML private Button cancelButton;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private FontIcon resetIcon;
    @FXML private Label fallbackIconLabel;

    private final ForgotPasswordService service =
            new ForgotPasswordService(ApiClient.getInstance());
    private String webmailUrl;

    @FXML
    private void initialize() {
        ensureResetIcon();
        emailField.textProperty().addListener((observable, oldValue, newValue) -> {
            emailError.setVisible(false);
            emailError.setManaged(false);
        });
        emailField.focusedProperty().addListener((observable, oldValue, focused) ->
                emailInputGroup.pseudoClassStateChanged(FOCUSED, focused));
    }

    @FXML
    private void handleSend() {
        String username = emailField.getText() == null ? "" : emailField.getText().trim();
        if (username.isEmpty()) {
            showEmailError("Vui lòng nhập tên đăng nhập.");
            return;
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            showEmailError("Tên đăng nhập chỉ chứa chữ cái, chữ số và dấu gạch dưới.");
            return;
        }
        String email = username + "@" + MAIL_DOMAIN;

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
        webmailUrl = response == null ? null : response.getWebmailUrl();
        boolean hasWebmailUrl = webmailUrl != null && !webmailUrl.isBlank();
        webmailLink.setVisible(hasWebmailUrl);
        webmailLink.setManaged(hasWebmailUrl);
        sendButton.setDisable(true);
        resizeDialog();
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
        webmailLink.setVisible(false);
        webmailLink.setManaged(false);
        resizeDialog();
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
    private void handleOpenWebmail() {
        if (webmailUrl == null || webmailUrl.isBlank()) {
            return;
        }
        try {
            LinkUtils.open(webmailUrl);
        } catch (Exception error) {
            webmailLink.setVisible(false);
            webmailLink.setManaged(false);
            resultLabel.setText("Không thể mở trình duyệt. Hãy sao chép: " + webmailUrl);
            resultLabel.setStyle("-fx-text-fill: #c81e4d;");
            resizeDialog();
        }
    }

    private void resizeDialog() {
        Platform.runLater(() -> {
            if (emailField.getScene() != null
                    && emailField.getScene().getWindow() instanceof Stage stage) {
                stage.sizeToScene();
                stage.setMinWidth(520);
                stage.setMinHeight(390);
            }
        });
    }

    @FXML
    private void handleCancel() {
        ((Stage) emailField.getScene().getWindow()).close();
    }
}
