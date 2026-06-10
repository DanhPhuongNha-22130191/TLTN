package secretchat.chat.view;

import javafx.application.Platform;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import secretchat.chat.viewmodel.ChatViewModel;

public final class ChatLifecycleHandler implements AutoCloseable {

    private final ChatViewModel viewModel;
    private final TextField sceneAnchor;
    private final AutoCloseable[] closeables;
    private boolean closed;

    public ChatLifecycleHandler(
            ChatViewModel viewModel, TextField sceneAnchor, AutoCloseable... closeables) {
        this.viewModel = viewModel;
        this.sceneAnchor = sceneAnchor;
        this.closeables = closeables;
    }

    public void initialize() {
        Platform.runLater(() -> {
            if (sceneAnchor.getScene() == null
                    || !(sceneAnchor.getScene().getWindow() instanceof Stage stage)) {
                return;
            }

            Runnable updateApplicationState = () -> viewModel.setApplicationActive(
                    stage.isShowing() && !stage.isIconified() && stage.isFocused());
            stage.focusedProperty().addListener((observable, oldValue, newValue) ->
                    updateApplicationState.run());
            stage.iconifiedProperty().addListener((observable, oldValue, newValue) ->
                    updateApplicationState.run());
            updateApplicationState.run();
            stage.setOnHidden(event -> close());
        });
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        viewModel.close();
        for (AutoCloseable closeable : closeables) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // UI cleanup must continue even if one helper has already been disposed.
            }
        }
    }
}
