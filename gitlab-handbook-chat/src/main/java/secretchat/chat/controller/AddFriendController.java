package secretchat.chat.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.function.Consumer;

public class AddFriendController {

    @FXML
    private TextField usernameField;

    @FXML
    private Label errorLabel;

    private Consumer<String> onFriendAdded;

    public void setOnFriendAdded(Consumer<String> callback) {
        this.onFriendAdded = callback;
    }

    @FXML
    private void handleAddFriend() {
        String username = usernameField.getText().trim();

        if (username.isEmpty()) {
            errorLabel.setText("Username không được để trống.");
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
            return;
        }

        if (onFriendAdded != null) {
            onFriendAdded.accept(username);
        }

        closeDialog();
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) usernameField.getScene().getWindow();
        stage.close();
    }
}
