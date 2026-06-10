package secretchat.util;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

public class UIUtils {

    private UIUtils() {
    }
    
    public static void makeDraggable(Node rootElement) {
        final double[] xOffset = {0};
        final double[] yOffset = {0};

        rootElement.setOnMousePressed(event -> {
            xOffset[0] = event.getSceneX();
            yOffset[0] = event.getSceneY();
        });

        rootElement.setOnMouseDragged(event -> {
            Stage stage = (Stage) rootElement.getScene().getWindow();
            if (stage != null) {
                stage.setX(event.getScreenX() - xOffset[0]);
                stage.setY(event.getScreenY() - yOffset[0]);
            }
        });
    }

    public static void closeWindowWhenMouseLeaves(Node rootElement) {
        rootElement.setOnMouseExited(event -> {
            if (rootElement.getScene() != null
                    && rootElement.getScene().getWindow() != null) {
                rootElement.getScene().getWindow().hide();
            }
        });
    }

    public static void showAutoClosingAlert(Alert alert) {
        closeWindowWhenMouseLeaves(alert.getDialogPane());
        alert.showAndWait();
    }
}
