package secretchat.chat.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import secretchat.chat.viewmodel.ChatViewModel;
import secretchat.dto.request.UpdateUserProfileRequest;
import secretchat.dto.response.UserResponse;

import java.util.regex.Pattern;

public class ProfileDialogController {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,50}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{9,11}$");

    @FXML private TextField usernameField;
    @FXML private TextField fullNameField;
    @FXML private TextField phoneNumberField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label errorLabel;
    @FXML private Button saveButton;
    @FXML private ProgressIndicator progressIndicator;

    private ChatViewModel viewModel;

    public void setViewModel(ChatViewModel viewModel) {
        this.viewModel = viewModel;
        UserResponse snapshot = viewModel.getCurrentUserProfileSnapshot();
        System.out.println("[Profile] Opening profile dialog. current snapshot keycloakUserId="
                + (snapshot == null ? "null" : snapshot.getKeycloakUserId()));
        if (snapshot != null) populate(snapshot);
        setLoading(true);
        viewModel.loadCurrentUserProfile()
                .whenComplete((profile, error) -> Platform.runLater(() -> {
                    setLoading(false);
                    if (error != null) {
                        System.out.println("[Profile] loadCurrentUserProfile failed: " + rootMessage(error));
                        showError("Không thể đồng bộ hồ sơ mới nhất. Bạn vẫn có thể chỉnh sửa và lưu.");
                        return;
                    }
                    System.out.println("[Profile] loadCurrentUserProfile success: username="
                            + profile.getUsername() + ", keycloakUserId=" + profile.getKeycloakUserId());
                    hideError();
                    populate(profile);
                }));
    }

    private void populate(UserResponse profile) {
        usernameField.setText(value(profile.getUsername()));
        fullNameField.setText(value(profile.getFullName()));
        phoneNumberField.setText(value(profile.getPhoneNumber()));
    }

    @FXML
    private void handleSave() {
        if (viewModel == null || !validate()) return;

        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setUsername(usernameField.getText().trim());
        request.setFullName(fullNameField.getText().trim());
        request.setPhoneNumber(phoneNumberField.getText().trim());
        request.setNewPassword(passwordField.getText().isBlank() ? null : passwordField.getText());

        System.out.println("[Profile] updateCurrentUserProfile request: username=" + request.getUsername()
                + ", fullName=" + request.getFullName() + ", phoneNumber=" + request.getPhoneNumber()
                + ", newPassword=" + (request.getNewPassword() == null ? "null" : "*****"));
        hideError();
        setLoading(true);
        viewModel.updateCurrentUserProfile(request)
                .whenComplete((profile, error) -> Platform.runLater(() -> {
                    setLoading(false);
                    if (error != null) {
                        System.out.println("[Profile] updateCurrentUserProfile failed: " + rootMessage(error));
                        showError(rootMessage(error));
                        return;
                    }
                    System.out.println("[Profile] updateCurrentUserProfile success: username=" + profile.getUsername()
                            + ", keycloakUserId=" + profile.getKeycloakUserId());
                    closeDialog();
                }));
    }

    private boolean validate() {
        String username = usernameField.getText().trim();
        String fullName = fullNameField.getText().trim();
        String phone = phoneNumberField.getText().trim();
        String password = passwordField.getText();

        if (!USERNAME_PATTERN.matcher(username).matches()) {
            showError("Username phải từ 3-50 ký tự, chỉ gồm chữ, số và dấu gạch dưới.");
            return false;
        }
        if (!phone.isBlank() && !PHONE_PATTERN.matcher(phone).matches()) {
            showError("Số điện thoại phải từ 9 đến 11 chữ số.");
            return false;
        }
        if (!password.isBlank() && password.length() < 6) {
            showError("Mật khẩu mới phải có ít nhất 6 ký tự.");
            return false;
        }
        if (!password.equals(confirmPasswordField.getText())) {
            showError("Xác nhận mật khẩu không khớp.");
            return false;
        }
        return true;
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void setLoading(boolean loading) {
        progressIndicator.setVisible(loading);
        progressIndicator.setManaged(loading);
        saveButton.setDisable(loading);
        usernameField.setDisable(loading);
        fullNameField.setDisable(loading);
        phoneNumberField.setDisable(loading);
        passwordField.setDisable(loading);
        confirmPasswordField.setDisable(loading);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "Không thể cập nhật trang cá nhân." : current.getMessage();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private void closeDialog() {
        ((Stage) usernameField.getScene().getWindow()).close();
    }
}
