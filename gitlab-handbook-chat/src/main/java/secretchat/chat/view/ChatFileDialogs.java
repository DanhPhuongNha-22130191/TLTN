package secretchat.chat.view;

import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;

public final class ChatFileDialogs {
    public static final long MAX_UPLOAD_BYTES = 100L * 1024 * 1024;

    public File chooseUpload(Window owner, boolean imagesOnly) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(imagesOnly ? "Chọn ảnh" : "Chọn file");
        if (imagesOnly) {
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(
                            "Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        }
        return chooser.showOpenDialog(owner);
    }

    public File chooseDownload(Window owner, String initialFileName) {
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName(initialFileName);
        return chooser.showSaveDialog(owner);
    }

    public boolean exceedsUploadLimit(File file) {
        return file != null && file.length() > MAX_UPLOAD_BYTES;
    }
}
