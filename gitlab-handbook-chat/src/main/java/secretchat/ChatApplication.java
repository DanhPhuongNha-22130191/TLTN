package secretchat;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class ChatApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(ChatApplication.class.getResource("/fxml/login-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        stage.setTitle("The GitLab Handbook Chat");
        stage.setScene(scene);
        stage.show();
    }
}
