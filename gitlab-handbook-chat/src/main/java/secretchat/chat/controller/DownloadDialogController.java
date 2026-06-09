package secretchat.chat.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

public class DownloadDialogController {

    @FXML private Label fileNameLabel;
    @FXML private Label fileSizeLabel;
    @FXML private FontIcon downloadIcon;
    @FXML private Label fallbackIconLabel;

    private Runnable onConfirm;

    public void setup(String fileName, String fileSize, Runnable onConfirm) {
        fileNameLabel.setText(fileName);
        fileSizeLabel.setText(fileSize != null ? fileSize : "Unknown size");
        ensureDownloadIcon();
        this.onConfirm = onConfirm;
    }

    private void ensureDownloadIcon() {
        try {
            downloadIcon.setIconLiteral("fa-download");
            boolean available = downloadIcon.getIconCode() != null;
            downloadIcon.setVisible(available);
            downloadIcon.setManaged(available);
            fallbackIconLabel.setVisible(!available);
            fallbackIconLabel.setManaged(!available);
        } catch (RuntimeException ex) {
            downloadIcon.setVisible(false);
            downloadIcon.setManaged(false);
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
        Stage stage = (Stage) fileNameLabel.getScene().getWindow();
        stage.close();
    }
}
