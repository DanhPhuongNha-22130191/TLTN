package secretchat.chat.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.function.Consumer;

public class CreateGroupController {

    @FXML
    private TextField groupNameField;

    @FXML
    private TextField groupDescField;

    @FXML
    private Label nameErrorLabel;

    /** Callback được gọi khi nhóm được tạo thành công */
    private java.util.function.BiConsumer<String, String> onGroupCreated;

    public void setOnGroupCreated(java.util.function.BiConsumer<String, String> callback) {
        this.onGroupCreated = callback;
    }

    @FXML
    private void handleCreate() {

        String name = groupNameField.getText().trim();
        String desc = groupDescField.getText().trim();

        if (name.isEmpty()) {
            nameErrorLabel.setText("Vui lòng nhập tên nhóm.");
            nameErrorLabel.setVisible(true);
            nameErrorLabel.setManaged(true);
            return;
        }

        if (name.length() < 2) {
            nameErrorLabel.setText("Tên nhóm phải có ít nhất 2 ký tự.");
            nameErrorLabel.setVisible(true);
            nameErrorLabel.setManaged(true);
            return;
        }

        if (onGroupCreated != null) {
            onGroupCreated.accept(name, desc);
        }

        closeDialog();
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) groupNameField.getScene().getWindow();
        stage.close();
    }
}
