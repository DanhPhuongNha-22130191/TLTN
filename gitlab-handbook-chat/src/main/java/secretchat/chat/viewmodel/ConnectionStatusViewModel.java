package secretchat.chat.viewmodel;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import secretchat.service.ConnectionStatusService;

public class ConnectionStatusViewModel {

    public static final String STYLE_CHECKING = "-fx-text-fill: #616161;";
    public static final String STYLE_SUCCESS = "-fx-text-fill: #2e7d32;";
    public static final String STYLE_FAILURE = "-fx-text-fill: #c62828;";

    private final ConnectionStatusService service = new ConnectionStatusService();

    private final StringProperty statusText = new SimpleStringProperty();
    private final StringProperty statusStyle = new SimpleStringProperty(STYLE_CHECKING);

    public StringProperty statusTextProperty() {
        return statusText;
    }

    public StringProperty statusStyleProperty() {
        return statusStyle;
    }

    public void refresh() {
        showChecking();

        String gatewayUrl = service.getGatewayUrl();
        service.checkGatewayAsync().whenComplete((connected, error) -> {
            boolean success = Boolean.TRUE.equals(connected) && error == null;
            Platform.runLater(() -> showResult(success, gatewayUrl));
        });
    }

    private void showChecking() {
        statusText.set("Đang kiểm tra kết nối server...");
        statusStyle.set(STYLE_CHECKING);
    }

    private void showResult(boolean connected, String gatewayUrl) {
        if (connected) {
            statusText.set("Kết nối server thành công");
            statusStyle.set(STYLE_SUCCESS);
        } else {
            statusText.set("Kết nối server thất bại");
            statusStyle.set(STYLE_FAILURE);
        }
    }
}
