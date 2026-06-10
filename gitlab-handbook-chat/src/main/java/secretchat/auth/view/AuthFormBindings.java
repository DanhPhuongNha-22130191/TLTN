package secretchat.auth.view;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;

public final class AuthFormBindings {
    private AuthFormBindings() {
    }

    public static void bindError(Label label, StringProperty error) {
        label.textProperty().bind(error);
        label.visibleProperty().bind(error.isNotEmpty());
        label.managedProperty().bind(label.visibleProperty());
    }

    public static void bindMessage(
            Label label, StringProperty message, StringProperty messageStyle) {
        label.textProperty().bind(message);
        label.styleProperty().bind(messageStyle);
        label.visibleProperty().bind(message.isNotEmpty());
        label.managedProperty().bind(label.visibleProperty());
    }

    public static void bindLoading(
            Pane loadingContainer, Button submitButton, BooleanProperty loading) {
        loadingContainer.visibleProperty().bind(loading);
        loadingContainer.managedProperty().bind(loadingContainer.visibleProperty());
        submitButton.disableProperty().bind(loading);
    }

    public static void bindPassword(
            PasswordField hiddenField,
            TextField visibleField,
            Button toggleButton,
            StringProperty password,
            BooleanProperty showPassword) {
        hiddenField.textProperty().bindBidirectional(password);
        visibleField.textProperty().bindBidirectional(password);
        hiddenField.visibleProperty().bind(showPassword.not());
        hiddenField.managedProperty().bind(hiddenField.visibleProperty());
        visibleField.visibleProperty().bind(showPassword);
        visibleField.managedProperty().bind(visibleField.visibleProperty());
        toggleButton.textProperty().bind(
                Bindings.when(showPassword).then("Hide").otherwise("Show"));
    }
}
