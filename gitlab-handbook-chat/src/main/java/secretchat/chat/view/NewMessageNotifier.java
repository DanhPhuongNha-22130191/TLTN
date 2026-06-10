package secretchat.chat.view;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import secretchat.chat.service.DesktopNotificationService;
import secretchat.chat.viewmodel.ChatViewModel;

public final class NewMessageNotifier implements AutoCloseable {
    private final DesktopNotificationService desktopNotifications =
            new DesktopNotificationService();
    private Popup activeToast;

    public void show(
            Stage stage, ChatViewModel.NewMessageEvent event, Runnable onOpen) {
        Runnable restoreAndOpen = () -> {
            stage.setIconified(false);
            stage.show();
            stage.toFront();
            stage.requestFocus();
            onOpen.run();
        };
        if (stage.isIconified() || !stage.isShowing()) {
            desktopNotifications.show(event.chatName(), event.preview(), restoreAndOpen);
        } else {
            showToast(stage, event, restoreAndOpen);
        }
    }

    private void showToast(
            Stage stage, ChatViewModel.NewMessageEvent event, Runnable onClick) {
        if (activeToast != null) activeToast.hide();
        Label title = new Label(event.chatName());
        title.getStyleClass().add("message-toast-title");
        Label preview = new Label(event.preview());
        preview.setWrapText(true);
        preview.setMaxWidth(300);
        preview.getStyleClass().add("message-toast-preview");
        VBox content = new VBox(4, title, preview);
        content.getStyleClass().add("message-toast");
        content.getStylesheets().add(
                getClass().getResource("/css/chat.css").toExternalForm());

        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.getContent().add(content);
        activeToast = popup;
        content.setOnMouseClicked(event1 -> {
            popup.hide();
            onClick.run();
        });

        content.applyCss();
        popup.show(stage);
        Platform.runLater(() -> {
            popup.setX(stage.getX() + stage.getWidth() - content.prefWidth(-1) - 24);
            popup.setY(stage.getY() + 72);
        });
        PauseTransition hide = new PauseTransition(Duration.seconds(5));
        hide.setOnFinished(event1 -> popup.hide());
        hide.play();
    }

    @Override
    public void close() {
        if (activeToast != null) activeToast.hide();
        desktopNotifications.close();
    }
}
