package secretchat.chat.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

public class NotificationDialogController {

    @FXML private Label titleLabel;
    @FXML private Label messageLabel;
    @FXML private FontIcon iconLabel;
    @FXML private Button okButton;

    public void setup(String title, String message) {
        titleLabel.setText(title);
        messageLabel.setText(message);
        
        if (title.toLowerCase().contains("lỗi") || title.toLowerCase().contains("thất bại")) {
            iconLabel.setIconLiteral("fa-times-circle");
            okButton.setStyle("-fx-background-color: #ff4d6d; -fx-text-fill: white;");
        } else if (title.toLowerCase().contains("thành công")) {
            iconLabel.setIconLiteral("fa-check-circle");
            okButton.setStyle("-fx-background-color: #10b981; -fx-text-fill: white;");
        } else {
            iconLabel.setIconLiteral("fa-info-circle");
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
