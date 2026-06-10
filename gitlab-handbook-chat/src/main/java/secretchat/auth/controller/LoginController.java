package secretchat.auth.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import secretchat.auth.view.AuthFormBindings;
import secretchat.auth.viewmodel.LoginViewModel;

import java.net.URL;
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
}
