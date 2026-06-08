package secretchat.chat.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.stage.Stage;

public class NotificationDialogController {

    @FXML private Label titleLabel;
    @FXML private Label messageLabel;
    @FXML private Label iconLabel;
    @FXML private Button okButton;

    public void setup(String title, String message) {
        titleLabel.setText(title);
        messageLabel.setText(message);
        
        if (title.toLowerCase().contains("lỗi") || title.toLowerCase().contains("thất bại")) {
            iconLabel.setText("❌");
            okButton.setStyle("-fx-background-color: #ff4d6d; -fx-text-fill: white;");
        } else if (title.toLowerCase().contains("thành công")) {
            iconLabel.setText("✅");
            okButton.setStyle("-fx-background-color: #10b981; -fx-text-fill: white;");
        } else {
            iconLabel.setText("ℹ️");
        }
    }

    @FXML
    private void handleOk() {
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) titleLabel.getScene().getWindow();
        stage.close();
    }
}
