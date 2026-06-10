package secretchat.chat.view;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.Callback;
import org.kordamp.ikonli.javafx.FontIcon;
import secretchat.chat.viewmodel.ChatViewModel;
import secretchat.dto.response.GroupResponse;

import java.util.function.Consumer;

public final class ConversationCellFactory {
    private ConversationCellFactory() {
    }

    public static Callback<ListView<String>, ListCell<String>> create(
            ChatViewModel viewModel,
            ListView<String> privateChats,
            ListView<String> groupChats,
            Consumer<String> viewProfile,
            Consumer<String> removeFriend) {
        return list -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("ai-list-cell");
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setGraphic(buildRow(
                        item, list, viewModel, privateChats, groupChats,
                        viewProfile, removeFriend, this));
            }
        };
    }

    private static HBox buildRow(
            String item,
            ListView<String> list,
            ChatViewModel viewModel,
            ListView<String> privateChats,
            ListView<String> groupChats,
            Consumer<String> viewProfile,
            Consumer<String> removeFriend,
            ListCell<String> cell) {
        HBox row = new HBox();
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        boolean isAi = "TRỢ LÝ AI".equals(item);

        if (isAi) {
            cell.getStyleClass().add("ai-list-cell");
            FontIcon icon = new FontIcon("fa-magic");
            icon.setIconSize(13);
            icon.setIconColor(Color.web("#8b5cf6"));
            row.getChildren().add(icon);
            HBox.setMargin(icon, new Insets(0, 8, 0, 0));
        }

        row.getChildren().add(new Label(item));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().add(spacer);

        int unread = unreadCount(item, list, viewModel, privateChats, groupChats);
        if (unread > 0) row.getChildren().add(unreadBadge(unread));
        if (isAi) row.getChildren().add(aiBadge());
        if (list == privateChats && !isAi) {
            row.getChildren().add(moreButton(item, viewProfile, removeFriend));
        }
        return row;
    }

    private static int unreadCount(
            String item,
            ListView<String> list,
            ChatViewModel viewModel,
            ListView<String> privateChats,
            ListView<String> groupChats) {
        if (list == privateChats) {
            String userId = viewModel.getUserIdByDisplayName(item);
            return userId == null ? 0 : viewModel.getUnreadCountForUser(userId);
        }
        if (list == groupChats) {
            GroupResponse group = viewModel.getGroupByName(item);
            return group == null ? 0 : viewModel.getUnreadCountForGroup(group.getId());
        }
        return 0;
    }

    private static Label unreadBadge(int unread) {
        Label badge = new Label(String.valueOf(unread));
        badge.getStyleClass().add("conversation-unread-badge");
        HBox.setMargin(badge, new Insets(0, 0, 0, 10));
        return badge;
    }

    private static Label aiBadge() {
        Label badge = new Label("AI");
        badge.setStyle("-fx-background-color: linear-gradient(to right, #8b5cf6, #ec4899);"
                + "-fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 1 5;"
                + "-fx-font-size: 9px; -fx-font-weight: bold;");
        HBox.setMargin(badge, new Insets(0, 0, 0, 6));
        return badge;
    }

    private static Button moreButton(
            String item, Consumer<String> viewProfile, Consumer<String> removeFriend) {
        Button button = new Button();
        FontIcon icon = new FontIcon("fa-ellipsis-v");
        icon.setIconSize(14);
        button.setGraphic(icon);
        button.getStyleClass().add("conversation-more-button");

        ContextMenu menu = new ContextMenu();
        MenuItem profile = new MenuItem("Xem hồ sơ");
        profile.setOnAction(event -> viewProfile.accept(item));
        MenuItem remove = new MenuItem("Xóa bạn bè");
        remove.getStyleClass().add("danger-menu-item");
        remove.setOnAction(event -> removeFriend.accept(item));
        menu.getItems().addAll(profile, remove);
        button.setOnAction(event -> {
            menu.show(button, javafx.geometry.Side.BOTTOM, 0, 0);
            if (menu.getScene() != null) {
                String stylesheet = ConversationCellFactory.class
                        .getResource("/css/chat.css").toExternalForm();
                if (!menu.getScene().getStylesheets().contains(stylesheet)) {
                    menu.getScene().getStylesheets().add(stylesheet);
                }
            }
            event.consume();
        });
        HBox.setMargin(button, new Insets(0, 0, 0, 8));
        return button;
    }
}
