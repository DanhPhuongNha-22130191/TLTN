package secretchat.auth.controller;

import javafx.beans.binding.Bindings;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
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
        bindPasswordFields();
        bindToggleButtons();
        bindErrorLabels();
        bindGlobalMessage();
        bindLoadingState();

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

    private void bindPasswordFields() {
        passwordField.textProperty().bindBidirectional(viewModel.passwordProperty());
        passwordTextField.textProperty().bindBidirectional(viewModel.passwordProperty());

        confirmPasswordField.textProperty().bindBidirectional(viewModel.confirmPasswordProperty());
        confirmPasswordTextField.textProperty().bindBidirectional(viewModel.confirmPasswordProperty());

        passwordField.visibleProperty().bind(viewModel.showPasswordProperty().not());
        passwordField.managedProperty().bind(viewModel.showPasswordProperty().not());

        passwordTextField.visibleProperty().bind(viewModel.showPasswordProperty());
        passwordTextField.managedProperty().bind(viewModel.showPasswordProperty());

        confirmPasswordField.visibleProperty().bind(viewModel.showConfirmPasswordProperty().not());
        confirmPasswordField.managedProperty().bind(viewModel.showConfirmPasswordProperty().not());

        confirmPasswordTextField.visibleProperty().bind(viewModel.showConfirmPasswordProperty());
        confirmPasswordTextField.managedProperty().bind(viewModel.showConfirmPasswordProperty());
    }

    private void bindToggleButtons() {
        togglePasswordBtn.textProperty().bind(
                Bindings.when(viewModel.showPasswordProperty())
                        .then("Hide")
                        .otherwise("Show")
        );

        toggleConfirmPasswordBtn.textProperty().bind(
                Bindings.when(viewModel.showConfirmPasswordProperty())
                        .then("Hide")
                        .otherwise("Show")
        );
    }

    private void bindErrorLabels() {
        bindErrorLabel(fullNameError, viewModel.fullNameErrorProperty());
        bindErrorLabel(usernameError, viewModel.usernameErrorProperty());
        bindErrorLabel(emailError, viewModel.emailErrorProperty());
        bindErrorLabel(phoneError, viewModel.phoneErrorProperty());
        bindErrorLabel(passwordError, viewModel.passwordErrorProperty());
        bindErrorLabel(confirmPasswordError, viewModel.confirmPasswordErrorProperty());
    }

    private void bindErrorLabel(Label label, StringProperty errorProperty) {
        label.textProperty().bind(errorProperty);
        label.visibleProperty().bind(errorProperty.isNotEmpty());
        label.managedProperty().bind(errorProperty.isNotEmpty());
    }

    private void bindGlobalMessage() {
        lblMessage.textProperty().bind(viewModel.globalMessageProperty());
        lblMessage.styleProperty().bind(viewModel.globalMessageStyleProperty());
        lblMessage.visibleProperty().bind(viewModel.globalMessageProperty().isNotEmpty());
        lblMessage.managedProperty().bind(viewModel.globalMessageProperty().isNotEmpty());
    }

    private void bindLoadingState() {
        loadingBox.visibleProperty().bind(viewModel.isLoadingProperty());
        loadingBox.managedProperty().bind(viewModel.isLoadingProperty());
        registerButton.disableProperty().bind(viewModel.isLoadingProperty());
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
