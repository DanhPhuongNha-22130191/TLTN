package secretchat.chat.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.stage.Stage;

public class MessageActionDialogController {

    @FXML private Label titleLabel;
    @FXML private Label messageLabel;
    @FXML private Label iconLabel;
    @FXML private Button confirmButton;

    private Runnable onConfirm;

    public void setup(String title, String message, String actionName, Runnable onConfirm) {
        titleLabel.setText(title);
        messageLabel.setText(message);
        confirmButton.setText(actionName);
        this.onConfirm = onConfirm;
        
        if (actionName.toLowerCase().contains("xóa")) {
            iconLabel.setText("🗑️");
            confirmButton.setStyle("-fx-background-color: #ff4d6d; -fx-text-fill: white;");
        } else if (actionName.toLowerCase().contains("thu hồi")) {
            iconLabel.setText("↩️");
            confirmButton.setStyle("-fx-background-color: #f59e0b; -fx-text-fill: white;");
        }
    }

    @FXML
    private void handleConfirm() {
        if (onConfirm != null) onConfirm.run();
        closeDialog();
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) titleLabel.getScene().getWindow();
        stage.close();
    }
}
