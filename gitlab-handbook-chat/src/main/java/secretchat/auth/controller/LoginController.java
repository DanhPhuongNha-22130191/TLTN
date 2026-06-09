package secretchat.auth.controller;

import javafx.beans.binding.Bindings;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
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
        bindPasswordFields();
        bindToggleButtons();
        bindErrorLabels();
        bindGlobalMessage();
        bindLoadingState();

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

    private void bindPasswordFields() {
        passwordField.textProperty().bindBidirectional(viewModel.passwordProperty());
        passwordTextField.textProperty().bindBidirectional(viewModel.passwordProperty());

        passwordField.visibleProperty().bind(viewModel.showPasswordProperty().not());
        passwordField.managedProperty().bind(viewModel.showPasswordProperty().not());

        passwordTextField.visibleProperty().bind(viewModel.showPasswordProperty());
        passwordTextField.managedProperty().bind(viewModel.showPasswordProperty());
    }

    private void bindToggleButtons() {
        togglePasswordBtn.textProperty().bind(
                Bindings.when(viewModel.showPasswordProperty())
                        .then("Hide")
                        .otherwise("Show")
        );
    }

    private void bindErrorLabels() {
        bindErrorLabel(usernameError, viewModel.usernameErrorProperty());
        bindErrorLabel(passwordError, viewModel.passwordErrorProperty());
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
