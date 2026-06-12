package secretchat;

import javafx.application.Application;

public class Launcher {
    public static void main(String[] args) {
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
        Application.launch(ChatApplication.class, args);
    }
}

