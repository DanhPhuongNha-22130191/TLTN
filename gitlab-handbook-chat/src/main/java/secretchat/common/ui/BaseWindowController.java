package secretchat.common.ui;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public abstract class BaseWindowController {
    private static final System.Logger LOGGER =
            System.getLogger(BaseWindowController.class.getName());

    @FXML
    protected void handleCloseWindow(ActionEvent event) {
        stageFrom(event).close();
    }

    @FXML
    protected void handleMinimizeWindow(ActionEvent event) {
        stageFrom(event).setIconified(true);
    }

    protected void switchScene(ActionEvent event, String fxmlPath) {
        switchScene(stageFrom(event), fxmlPath);
    }

    protected void switchScene(Stage stage, String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Không thể chuyển trang: " + fxmlPath, e);
        }
    }

    private Stage stageFrom(ActionEvent event) {
        return (Stage) ((Node) event.getSource()).getScene().getWindow();
    }
}
