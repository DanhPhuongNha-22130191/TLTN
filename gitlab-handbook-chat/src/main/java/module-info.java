module secretchat.secrectchat {

    requires java.net.http;

    requires javafx.controls;
    requires javafx.fxml;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome;

    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;

    requires io.github.cdimascio.dotenv.java;
    requires java.desktop;

    // ── Auth module ──────────────────────────────────────────────────────────
    opens secretchat.auth.controller   to javafx.fxml;
    opens secretchat.auth.viewmodel    to javafx.fxml;
    opens secretchat.auth.dto.request  to com.fasterxml.jackson.databind;
    opens secretchat.auth.dto.response to com.fasterxml.jackson.databind;
    opens secretchat.dto.request      to com.fasterxml.jackson.databind;
    opens secretchat.dto.response     to com.fasterxml.jackson.databind;

    // ── Chat module ──────────────────────────────────────────────────────────
    opens secretchat.chat.controller   to javafx.fxml;
    opens secretchat.chat.viewmodel    to javafx.fxml;
    opens secretchat.common.ui         to javafx.fxml;

    // ── Application entry point ──────────────────────────────────────────────
    opens secretchat to javafx.fxml;

    exports secretchat;
}
