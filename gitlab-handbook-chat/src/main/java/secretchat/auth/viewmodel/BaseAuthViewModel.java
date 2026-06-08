package secretchat.auth.viewmodel;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public abstract class BaseAuthViewModel {

    // Common Inputs
    protected final StringProperty username = new SimpleStringProperty("");
    protected final StringProperty password = new SimpleStringProperty("");

    // Common Error Messages
    protected final StringProperty usernameError = new SimpleStringProperty("");
    protected final StringProperty passwordError = new SimpleStringProperty("");
    protected final StringProperty globalMessage = new SimpleStringProperty("");
    protected final StringProperty globalMessageStyle = new SimpleStringProperty("");

    // Common UI States
    protected final BooleanProperty isLoading = new SimpleBooleanProperty(false);
    protected final BooleanProperty showPassword = new SimpleBooleanProperty(false);

    // Properties getters
    public StringProperty usernameProperty() { return username; }
    public StringProperty passwordProperty() { return password; }

    public StringProperty usernameErrorProperty() { return usernameError; }
    public StringProperty passwordErrorProperty() { return passwordError; }

    public StringProperty globalMessageProperty() { return globalMessage; }
    public StringProperty globalMessageStyleProperty() { return globalMessageStyle; }

    public BooleanProperty isLoadingProperty() { return isLoading; }
    public BooleanProperty showPasswordProperty() { return showPassword; }

    // Common Methods
    public void togglePasswordVisibility() {
        showPassword.set(!showPassword.get());
    }

    // Abstract method for validation, implemented by subclasses
    public abstract boolean validateInput();
}
