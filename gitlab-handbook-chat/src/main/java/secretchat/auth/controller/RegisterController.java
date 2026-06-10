package secretchat.auth.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import secretchat.auth.view.AuthFormBindings;
import secretchat.auth.viewmodel.RegisterViewModel;

import java.net.URL;
import java.util.ResourceBundle;

public class RegisterController extends BaseAuthController implements Initializable {

    @FXML private TextField fullNameField;
    @FXML private Label fullNameError;

    @FXML private TextField usernameField;
    @FXML private Label usernameError;

    @FXML private TextField emailField;
    @FXML private Label emailError;

    @FXML private TextField phoneNumberField;
    @FXML private Label phoneError;

    @FXML private PasswordField passwordField;
    @FXML private TextField passwordTextField;
    @FXML private Label passwordError;
    @FXML private Button togglePasswordBtn;

    @FXML private PasswordField confirmPasswordField;
    @FXML private TextField confirmPasswordTextField;
    @FXML private Label confirmPasswordError;
    @FXML private Button toggleConfirmPasswordBtn;

    @FXML private CheckBox termsCheckBox;

    @FXML private Label lblMessage;
    @FXML private Button registerButton;
    @FXML private HBox loadingBox;

    private final RegisterViewModel viewModel = new RegisterViewModel();

    @Override
    public void initialize(URL url, ResourceBundle resources) {
        bindInputs();
        bindPasswords();
        bindFeedback();

        viewModel.registerSuccessProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                javafx.application.Platform.runLater(() -> {
                    Stage stage = (Stage) registerButton.getScene().getWindow();
                    switchScene(stage, "/fxml/login-view.fxml");
                });
            }
        });
    }

    private void bindInputs() {
        fullNameField.textProperty().bindBidirectional(viewModel.fullNameProperty());
        usernameField.textProperty().bindBidirectional(viewModel.usernameProperty());
        emailField.textProperty().bindBidirectional(viewModel.emailProperty());
        phoneNumberField.textProperty().bindBidirectional(viewModel.phoneNumberProperty());
        termsCheckBox.selectedProperty().bindBidirectional(viewModel.termsAcceptedProperty());
    }

    private void bindPasswords() {
        AuthFormBindings.bindPassword(
                passwordField, passwordTextField, togglePasswordBtn,
                viewModel.passwordProperty(), viewModel.showPasswordProperty());
        AuthFormBindings.bindPassword(
                confirmPasswordField, confirmPasswordTextField, toggleConfirmPasswordBtn,
                viewModel.confirmPasswordProperty(), viewModel.showConfirmPasswordProperty());
    }

    private void bindFeedback() {
        AuthFormBindings.bindError(fullNameError, viewModel.fullNameErrorProperty());
        AuthFormBindings.bindError(usernameError, viewModel.usernameErrorProperty());
        AuthFormBindings.bindError(emailError, viewModel.emailErrorProperty());
        AuthFormBindings.bindError(phoneError, viewModel.phoneErrorProperty());
        AuthFormBindings.bindError(passwordError, viewModel.passwordErrorProperty());
        AuthFormBindings.bindError(confirmPasswordError, viewModel.confirmPasswordErrorProperty());
        AuthFormBindings.bindMessage(
                lblMessage, viewModel.globalMessageProperty(), viewModel.globalMessageStyleProperty());
        AuthFormBindings.bindLoading(loadingBox, registerButton, viewModel.isLoadingProperty());
    }

    @FXML
    private void handleRegister() {
        viewModel.registerAsync();
    }

    @FXML
    private void togglePasswordVisibility(ActionEvent event) {
        viewModel.togglePasswordVisibility();
    }

    @FXML
    private void toggleConfirmPasswordVisibility(ActionEvent event) {
        viewModel.toggleConfirmPasswordVisibility();
    }

    @FXML
    private void goToLogin(ActionEvent event) {
        switchScene(event, "/fxml/login-view.fxml");
    }
}
