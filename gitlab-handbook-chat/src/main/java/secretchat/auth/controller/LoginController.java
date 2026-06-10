package secretchat.auth.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import secretchat.auth.view.AuthFormBindings;
import secretchat.auth.viewmodel.LoginViewModel;

import java.net.URL;
import java.io.IOException;
import java.util.ResourceBundle;

public class LoginController extends BaseAuthController implements Initializable {

    @FXML private TextField usernameField;
    @FXML private Label usernameError;

    @FXML private PasswordField passwordField;
    @FXML private TextField passwordTextField;
    @FXML private Label passwordError;
    @FXML private Button togglePasswordBtn;

    @FXML private Label lblMessage;
    @FXML private Button registerButton; // Login button in the FXML
    @FXML private HBox loadingBox;
    @FXML private Button goToLoginBtn; // Sign-up link button in the FXML

    private final LoginViewModel viewModel = new LoginViewModel();

    @Override
    public void initialize(URL url, ResourceBundle resources) {
        bindInputs();
        AuthFormBindings.bindPassword(
                passwordField, passwordTextField, togglePasswordBtn,
                viewModel.passwordProperty(), viewModel.showPasswordProperty());
        AuthFormBindings.bindError(usernameError, viewModel.usernameErrorProperty());
        AuthFormBindings.bindError(passwordError, viewModel.passwordErrorProperty());
        AuthFormBindings.bindMessage(
                lblMessage, viewModel.globalMessageProperty(), viewModel.globalMessageStyleProperty());
        AuthFormBindings.bindLoading(loadingBox, registerButton, viewModel.isLoadingProperty());

        viewModel.loginSuccessProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                javafx.application.Platform.runLater(() -> {
                    Stage stage = (Stage) registerButton.getScene().getWindow();
                    switchScene(stage, "/fxml/chat-view.fxml");
                });
            }
        });
    }

    private void bindInputs() {
        usernameField.textProperty().bindBidirectional(viewModel.usernameProperty());
    }

    @FXML
    public void handleLogin(ActionEvent event) {
        viewModel.loginAsync();
    }

    @FXML
    public void goToRegister(ActionEvent event) {
        switchScene(event, "/fxml/register-view.fxml");
    }

    @FXML
    private void togglePasswordVisibility(ActionEvent event) {
        viewModel.togglePasswordVisibility();
    }

    @FXML
    private void handleForgotPassword(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/forgot-password-dialog.fxml"));
            Parent root = loader.load();
            Stage dialog = new Stage();
            dialog.initOwner((Stage) usernameField.getScene().getWindow());
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initStyle(StageStyle.UNDECORATED);
            dialog.setScene(new Scene(root));
            dialog.setResizable(false);
            dialog.showAndWait();
        } catch (IOException error) {
            lblMessage.setText("Không thể mở chức năng quên mật khẩu.");
            lblMessage.setStyle("-fx-text-fill: #ff4d6d;");
            lblMessage.setVisible(true);
            lblMessage.setManaged(true);
        }
    }
}
