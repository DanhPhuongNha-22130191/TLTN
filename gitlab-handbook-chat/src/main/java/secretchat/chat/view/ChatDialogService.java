package secretchat.chat.view;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import secretchat.chat.controller.DownloadDialogController;
import secretchat.chat.controller.MessageActionDialogController;
import secretchat.chat.controller.NotificationDialogController;

import java.util.function.Consumer;

public final class ChatDialogService {
    private static final System.Logger LOGGER =
            System.getLogger(ChatDialogService.class.getName());
    private final Class<?> resourceAnchor;

    public ChatDialogService(Class<?> resourceAnchor) {
        this.resourceAnchor = resourceAnchor;
    }

    public <T> void show(
            String fxmlPath, Window owner, Consumer<T> configureController) {
        try {
            FXMLLoader loader = new FXMLLoader(resourceAnchor.getResource(fxmlPath));
            Parent root = loader.load();
            configureController.accept(loader.getController());
            showStage(root, owner);
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Không thể mở dialog: " + fxmlPath, e);
        }
    }

    public void showMessageAction(
            Window owner, String title, String message, String actionName, Runnable onConfirm) {
        show("/fxml/message-action-dialog.fxml", owner,
                (MessageActionDialogController controller) ->
                        controller.setup(title, message, actionName, onConfirm));
    }

    public void showDownload(
            Window owner, String fileName, String fileSize, Runnable onConfirm) {
        show("/fxml/download-dialog.fxml", owner,
                (DownloadDialogController controller) ->
                        controller.setup(fileName, fileSize, onConfirm));
    }

    public void showNotification(String title, String content) {
        Platform.runLater(() -> show("/fxml/notification-dialog.fxml", null,
                (NotificationDialogController controller) -> controller.setup(title, content)));
    }

    public boolean confirm(String title, String header, String content) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        style(confirm);
        confirm.setTitle(title);
        confirm.setHeaderText(header);
        confirm.setContentText(content);
        return confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void showStage(Parent root, Window owner) {
        Stage dialog = new Stage();
        if (owner != null) dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);
        dialog.setScene(new Scene(root));
        dialog.setResizable(false);
        dialog.showAndWait();
    }

    private void style(Alert alert) {
        try {
            alert.getDialogPane().getStylesheets().add(
                    resourceAnchor.getResource("/css/chat.css").toExternalForm());
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Không thể tải CSS cho dialog", e);
        }
    }
}
