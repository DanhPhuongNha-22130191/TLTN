package secretchat.chat.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

public class MessageActionDialogController {

    @FXML private Label titleLabel;
    @FXML private Label messageLabel;
    @FXML private FontIcon actionIcon;
    @FXML private Label fallbackIconLabel;
    @FXML private Button confirmButton;

    private Runnable onConfirm;

    public void setup(String title, String message, String actionName, Runnable onConfirm) {
        titleLabel.setText(title);
        messageLabel.setText(message);
        confirmButton.setText(actionName);
        this.onConfirm = onConfirm;

        if (actionName.toLowerCase().contains("xóa")) {
            setActionIcon("fa-trash", "X");
            confirmButton.setStyle("-fx-background-color: #ff4d6d; -fx-text-fill: white;");
        } else if (actionName.toLowerCase().contains("thu hồi")) {
            setActionIcon("fa-undo", "<");
            confirmButton.setStyle("-fx-background-color: #f59e0b; -fx-text-fill: white;");
        } else {
            setActionIcon("fa-cog", "!");
        }
    }

    private void setActionIcon(String iconLiteral, String fallbackText) {
        fallbackIconLabel.setText(fallbackText);
        try {
            actionIcon.setIconLiteral(iconLiteral);
            boolean available = actionIcon.getIconCode() != null;
            actionIcon.setVisible(available);
            actionIcon.setManaged(available);
            fallbackIconLabel.setVisible(!available);
            fallbackIconLabel.setManaged(!available);
        } catch (RuntimeException ex) {
            actionIcon.setVisible(false);
            actionIcon.setManaged(false);
            fallbackIconLabel.setVisible(true);
            fallbackIconLabel.setManaged(true);
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
