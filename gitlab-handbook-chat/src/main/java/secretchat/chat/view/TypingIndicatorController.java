package secretchat.chat.view;

import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.util.Duration;
import secretchat.chat.viewmodel.ChatViewModel;

public final class TypingIndicatorController implements AutoCloseable {

    private final ChatViewModel viewModel;
    private final Label typingLabel;
    private final TextField messageInput;
    private final PauseTransition typingPause = new PauseTransition(Duration.millis(900));
    private final Timeline animation = new Timeline();
    private final ChangeListener<String> inputListener;
    private final ChangeListener<String> typingTextListener;
    private String baseText = "Đang nhập";

    public TypingIndicatorController(
            ChatViewModel viewModel, Label typingLabel, TextField messageInput) {
        this.viewModel = viewModel;
        this.typingLabel = typingLabel;
        this.messageInput = messageInput;
        this.inputListener = (observable, oldText, newText) -> handleInputChanged(newText);
        this.typingTextListener = (observable, oldText, newText) -> updateIndicator(newText);
    }

    public void initialize() {
        typingLabel.setVisible(false);
        typingLabel.setManaged(false);
        animation.setCycleCount(Timeline.INDEFINITE);
        animation.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO, event -> typingLabel.setText(baseText + ".")),
                new KeyFrame(Duration.millis(300), event -> typingLabel.setText(baseText + "..")),
                new KeyFrame(Duration.millis(600), event -> typingLabel.setText(baseText + "...")));

        typingPause.setOnFinished(event -> viewModel.sendTyping(false));
        messageInput.textProperty().addListener(inputListener);
        viewModel.typingTextProperty().addListener(typingTextListener);
        updateIndicator(viewModel.typingTextProperty().get());
    }

    public void stopLocalTyping() {
        typingPause.stop();
        viewModel.sendTyping(false);
    }

    private void handleInputChanged(String text) {
        boolean typing = text != null && !text.isBlank();
        viewModel.sendTyping(typing);
        typingPause.stop();
        if (typing) {
            typingPause.playFromStart();
        }
    }

    private void updateIndicator(String text) {
        boolean visible = text != null && !text.isBlank();
        typingLabel.setVisible(visible);
        typingLabel.setManaged(visible);
        if (!visible) {
            animation.stop();
            typingLabel.setText("");
            return;
        }

        baseText = text.replaceFirst("\\.{1,3}$", "");
        animation.playFromStart();
    }

    @Override
    public void close() {
        typingPause.stop();
        animation.stop();
        messageInput.textProperty().removeListener(inputListener);
        viewModel.typingTextProperty().removeListener(typingTextListener);
    }
}
