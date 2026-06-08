package secretchat.chat.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.stage.Stage;

public class DownloadDialogController {

    @FXML private Label fileNameLabel;
    @FXML private Label fileSizeLabel;
    @FXML private Label iconLabel;

    private Runnable onConfirm;

    public void setup(String fileName, String fileSize, Runnable onConfirm) {
        fileNameLabel.setText(fileName);
        fileSizeLabel.setText(fileSize != null ? fileSize : "Unknown size");
        iconLabel.setText("⬇️");
        this.onConfirm = onConfirm;
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
        Stage stage = (Stage) fileNameLabel.getScene().getWindow();
        stage.close();
    }
}
