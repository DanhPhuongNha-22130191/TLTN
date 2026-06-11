package secretchat.auth.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import secretchat.util.LinkUtils;

public class MailboxCredentialsController {
    @FXML private TextField emailField;
    @FXML private TextField passwordField;
    @FXML private TextField webmailField;
    @FXML private Label copiedLabel;
    @FXML private Label openErrorLabel;

    public void setup(String email, String password, String webmailUrl) {
        emailField.setText(email);
        passwordField.setText(password);
        webmailField.setText(webmailUrl);
    }

    @FXML
    private void handleCopy() {
        ClipboardContent content = new ClipboardContent();
        content.putString(
                "Email: " + emailField.getText()
                        + System.lineSeparator()
                        + "Mật khẩu mail: " + passwordField.getText()
                        + System.lineSeparator()
                        + "Webmail: " + webmailField.getText());
        Clipboard.getSystemClipboard().setContent(content);
        copiedLabel.setVisible(true);
        copiedLabel.setManaged(true);
    }

    @FXML
    private void handleOpenWebmail() {
        openErrorLabel.setVisible(false);
        openErrorLabel.setManaged(false);
        try {
            LinkUtils.open(webmailField.getText());
        } catch (Exception error) {
            openErrorLabel.setText(
                    "Không thể mở trình duyệt. Hãy sao chép đường dẫn webmail ở trên.");
            openErrorLabel.setVisible(true);
            openErrorLabel.setManaged(true);
        }
    }

    @FXML
    private void handleWebmailFieldClick(MouseEvent event) {
        if (event.isStillSincePress() && webmailField.getSelectedText().isEmpty()) {
            handleOpenWebmail();
        }
    }

    @FXML
    private void handleClose() {
        ((Stage) emailField.getScene().getWindow()).close();
    }
}
