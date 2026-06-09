package secretchat.chat.controller;

import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.util.Duration;
import secretchat.chat.viewmodel.ConnectionStatusViewModel;

import java.net.URL;
import java.util.ResourceBundle;

public class ConnectionStatusController implements Initializable {

    @FXML
    private Label statusLabel;

    private final ConnectionStatusViewModel viewModel = new ConnectionStatusViewModel();

    private final PauseTransition hideTimer =
            new PauseTransition(Duration.seconds(5));

    @Override
    public void initialize(URL url, ResourceBundle resources) {

        statusLabel.setVisible(false);

        statusLabel.textProperty().bind(viewModel.statusTextProperty());
        statusLabel.getStyleClass().add("status-label");

        viewModel.statusTextProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && !newValue.isBlank()) {
                statusLabel.setVisible(true);

                hideTimer.stop();
                hideTimer.setOnFinished(e -> statusLabel.setVisible(false));
                hideTimer.playFromStart();
            }
        });

        viewModel.refresh();
    }

    public ConnectionStatusViewModel getViewModel() {
        return viewModel;
    }
}
