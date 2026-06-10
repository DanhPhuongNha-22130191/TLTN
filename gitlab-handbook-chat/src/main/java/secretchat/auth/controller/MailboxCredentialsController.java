package secretchat.auth.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Stage;

public class MailboxCredentialsController {
    @FXML private Label emailLabel;
    @FXML private Label passwordLabel;
    @FXML private Label webmailLabel;
    @FXML private Label copiedLabel;

    public void setup(String email, String password, String webmailUrl) {
        emailLabel.setText(email);
        passwordLabel.setText(password);
        webmailLabel.setText(webmailUrl);
    }

    @FXML
    private void handleCopy() {
        ClipboardContent content = new ClipboardContent();
        content.putString(
                "Email: " + emailLabel.getText()
                        + System.lineSeparator()
                        + "Mật khẩu mail: " + passwordLabel.getText()
                        + System.lineSeparator()
                        + "Webmail: " + webmailLabel.getText());
        Clipboard.getSystemClipboard().setContent(content);
        copiedLabel.setVisible(true);
        copiedLabel.setManaged(true);
    }

    @FXML
    private void handleClose() {
        ((Stage) emailLabel.getScene().getWindow()).close();
    }
}
