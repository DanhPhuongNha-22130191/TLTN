package secretchat.chat.view;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import secretchat.util.FileUtils;

import java.io.File;
import java.util.function.BiConsumer;

public final class FilePreviewController {

    private final TextField messageInput;
    private final HBox previewBox;
    private final Label fileNameLabel;
    private final Label fileSizeLabel;
    private final ChatFileDialogs fileDialogs;
    private final BiConsumer<String, String> alertHandler;
    private File selectedFile;

    public FilePreviewController(
            TextField messageInput,
            HBox previewBox,
            Label fileNameLabel,
            Label fileSizeLabel,
            ChatFileDialogs fileDialogs,
            BiConsumer<String, String> alertHandler) {
        this.messageInput = messageInput;
        this.previewBox = previewBox;
        this.fileNameLabel = fileNameLabel;
        this.fileSizeLabel = fileSizeLabel;
        this.fileDialogs = fileDialogs;
        this.alertHandler = alertHandler;
    }

    public void chooseFile(boolean imagesOnly) {
        File candidate = fileDialogs.chooseUpload(messageInput.getScene().getWindow(), imagesOnly);
        if (candidate == null) {
            return;
        }
        if (fileDialogs.exceedsUploadLimit(candidate)) {
            alertHandler.accept("Không thể gửi file", fileDialogs.uploadLimitMessage(candidate));
            clear();
            return;
        }

        selectedFile = candidate;
        fileNameLabel.setText(candidate.getName());
        fileSizeLabel.setText(FileUtils.formatFileSize(candidate.length()));
        previewBox.setVisible(true);
        previewBox.setManaged(true);
    }

    public File selectedFile() {
        return selectedFile;
    }

    public void clear() {
        selectedFile = null;
        fileNameLabel.setText("");
        fileSizeLabel.setText("");
        previewBox.setVisible(false);
        previewBox.setManaged(false);
    }
}
