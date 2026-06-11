package secretchat.chat.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import secretchat.chat.viewmodel.ChatViewModel;
import secretchat.dto.response.FriendResponse;

public class FriendRequestsController {

    @FXML private ListView<FriendResponse> requestList;
    @FXML private Label statusLabel;
    @FXML private Button acceptButton;
    @FXML private Button rejectButton;

    private ChatViewModel viewModel;

    @FXML
    private void initialize() {
        requestList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(FriendResponse request, boolean empty) {
                super.updateItem(request, empty);
                setText(empty || request == null ? null : displayName(request));
            }
        });
        requestList.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> updateActions(selected != null));
        updateActions(false);
    }

    public void setViewModel(ChatViewModel viewModel) {
        this.viewModel = viewModel;
        reload();
    }

    @FXML
    private void handleAccept() {
        FriendResponse selected = requestList.getSelectionModel().getSelectedItem();
        if (selected == null || viewModel == null) return;
        setBusy("Đang chấp nhận lời mời...");
        viewModel.acceptFriendRequest(selected)
                .whenComplete((ignored, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        showError("Không thể chấp nhận lời mời: " + rootMessage(error));
                        return;
                    }
                    requestList.getItems().remove(selected);
                    showEmptyState();
                }));
    }

    @FXML
    private void handleReject() {
        FriendResponse selected = requestList.getSelectionModel().getSelectedItem();
        if (selected == null || viewModel == null) return;
        setBusy("Đang từ chối lời mời...");
        viewModel.rejectFriendRequest(selected)
                .whenComplete((ignored, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        showError("Không thể từ chối lời mời: " + rootMessage(error));
                        return;
                    }
                    requestList.getItems().remove(selected);
                    showEmptyState();
                }));
    }

    @FXML
    private void handleClose() {
        ((Stage) requestList.getScene().getWindow()).close();
    }

    private void reload() {
        setBusy("Đang tải lời mời kết bạn...");
        viewModel.loadIncomingFriendRequests()
                .whenComplete((requests, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        showError("Không thể tải lời mời: " + rootMessage(error));
                        return;
                    }
                    requestList.getItems().setAll(requests);
                    showEmptyState();
                }));
    }

    private void showEmptyState() {
        boolean empty = requestList.getItems().isEmpty();
        statusLabel.setText(empty ? "Bạn chưa có lời mời kết bạn mới."
                : "Chọn một lời mời để xử lý.");
        statusLabel.getStyleClass().remove("error-label");
        updateActions(requestList.getSelectionModel().getSelectedItem() != null);
    }

    private void setBusy(String message) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().remove("error-label");
        updateActions(false);
    }

    private void showError(String message) {
        statusLabel.setText(message);
        if (!statusLabel.getStyleClass().contains("error-label")) {
            statusLabel.getStyleClass().add("error-label");
        }
        updateActions(requestList.getSelectionModel().getSelectedItem() != null);
    }

    private void updateActions(boolean enabled) {
        acceptButton.setDisable(!enabled);
        rejectButton.setDisable(!enabled);
    }

    private String displayName(FriendResponse request) {
        String username = request.getFriendUsername();
        return username == null || username.isBlank()
                ? "Người dùng " + request.getFriendId()
                : username;
    }

    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}

