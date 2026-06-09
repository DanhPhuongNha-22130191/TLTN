package secretchat.chat.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import secretchat.chat.viewmodel.MainViewModel;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller for the main chat window (main-view.fxml).
 * Handles navigation (logout) and delegates business logic to {@link MainViewModel}.
 */
public class MainController extends BaseChatController implements Initializable {

    private final MainViewModel viewModel = new MainViewModel();

    @Override
    public void initialize(URL url, ResourceBundle resources) {
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        viewModel.logout();
        switchScene(event, "/fxml/login-view.fxml");
    }
}
