package secretchat.chat.view;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.collections.MapChangeListener;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import secretchat.chat.viewmodel.ChatViewModel;
import secretchat.util.FileUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.function.BiConsumer;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ChatMessagePane {
    private final ChatViewModel viewModel;
    private final VBox container;
    private final ScrollPane scrollPane;
    private final MessageContextMenuFactory menus;
    private final ChatDialogService dialogs;
    private final ChatFileDialogs fileDialogs;
    private final BiConsumer<String, String> notification;

    public ChatMessagePane(
            ChatViewModel viewModel,
            VBox container,
            ScrollPane scrollPane,
            MessageContextMenuFactory menus,
            ChatDialogService dialogs,
            ChatFileDialogs fileDialogs,
            BiConsumer<String, String> notification) {
        this.viewModel = viewModel;
        this.container = container;
        this.scrollPane = scrollPane;
        this.menus = menus;
        this.dialogs = dialogs;
        this.fileDialogs = fileDialogs;
        this.notification = notification;
    }

    public void renderAll() {
        container.getChildren().clear();
        viewModel.getMessages().forEach(this::render);
        scrollBottom();
    }

    public void render(ChatViewModel.MessageItem item) {
        HBox wrapper = new HBox();
        wrapper.getStyleClass().addAll(
                "message-row", item.isMe() ? "my-message-row" : "other-message-row");
        wrapper.setAlignment(item.isMe() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox messageBox = new VBox(item.isMe() ? 2 : 4);
        messageBox.setAlignment(item.isMe() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        if (!item.isMe()) messageBox.getChildren().add(senderLabel(item));

        Node content = item.isFile() ? createFileMessage(item) : createTextMessage(item);
        HBox contentRow = createContentRow(wrapper, content, item);
        messageBox.getChildren().add(contentRow);
        if (!item.isDeleted() && !item.isDeletedForMe()) {
            messageBox.getChildren().add(reactionBar(item));
        }
        messageBox.getChildren().add(timeLabel(item));
        if (item.isMe()) messageBox.getChildren().add(statusLabel(item));

        wrapper.getChildren().add(messageBox);
        container.getChildren().add(wrapper);
    }

    public void scrollBottom() {
        Platform.runLater(() -> {
            container.applyCss();
            container.layout();
            scrollPane.applyCss();
            scrollPane.layout();
            Platform.runLater(() -> scrollPane.setVvalue(1.0));
        });
    }

    public void scrollTo(ChatViewModel.MessageItem target) {
        if (target == null) return;
        int index = viewModel.getMessages().indexOf(target);
        if (index < 0 || index >= container.getChildren().size()) return;
        Node row = container.getChildren().get(index);
        Node highlight = row instanceof HBox box && !box.getChildren().isEmpty()
                ? box.getChildren().getFirst() : row;
        highlight(highlight);
        Platform.runLater(() -> centerOn(row));
    }

    private Label senderLabel(ChatViewModel.MessageItem item) {
        Label label = new Label(item.getSenderName());
        label.getStyleClass().add("message-sender");
        return label;
    }

    private Node createTextMessage(ChatViewModel.MessageItem item) {
        if (!item.isMe() && "TRỢ LÝ AI".equals(item.getSenderName())) {
            VBox bubble = AiMessageView.create(item.getContent());
            item.contentProperty().addListener(
                    (obs, oldText, newText) -> AiMessageView.update(bubble, newText));
            return bubble;
        }
        String textStyle = item.isMe() ? "my-message-text" : "other-message-text";
        TextFlow bubble = LinkTextView.create(item.getContent(), textStyle);
        bubble.setMaxWidth(350);
        bubble.getStyleClass().addAll(
                "chat-message-bubble", item.isMe() ? "my-message" : "other-message");
        item.contentProperty().addListener(
                (obs, oldText, newText) -> LinkTextView.update(bubble, newText, textStyle));
        return bubble;
    }

    private Node createFileMessage(ChatViewModel.MessageItem item) {
        HBox fileBox = new HBox(12);
        fileBox.setAlignment(Pos.CENTER_LEFT);
        fileBox.getStyleClass().add(item.isMe() ? "chat-file-message" : "other-file-message");

        StackPane icon = fileIcon(item);
        VBox details = new VBox(3);
        Label name = new Label(item.getContent());
        name.setWrapText(true);
        name.getStyleClass().add("file-message-name");
        Label size = new Label(item.getResponse() != null && item.getResponse().getFileSize() != null
                ? FileUtils.formatFileSize(item.getResponse().getFileSize()) : "");
        size.getStyleClass().add("file-message-size");
        details.getChildren().addAll(name, size);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        fileBox.getChildren().addAll(icon, details, spacer, uploadProgress(item));
        attachDownload(fileBox, item);
        return fileBox;
    }

    private StackPane fileIcon(ChatViewModel.MessageItem item) {
        FontIcon fileIcon = new FontIcon("fa-file");
        fileIcon.setIconSize(36);
        fileIcon.getStyleClass().add("file-message-icon");
        Label extension = new Label(fileExtension(item.getContent()));
        extension.setStyle(item.isMe()
                ? "-fx-font-size: 9px; -fx-font-weight: 900; -fx-text-fill: #6366f1;"
                    + "-fx-background-color: white; -fx-padding: 1 4; -fx-background-radius: 4;"
                : "-fx-font-size: 9px; -fx-font-weight: 900; -fx-text-fill: white;"
                    + "-fx-background-color: #6366f1; -fx-padding: 1 4; -fx-background-radius: 4;");
        StackPane.setAlignment(extension, Pos.CENTER);
        StackPane.setMargin(extension, new Insets(8, 0, 0, 0));
        return new StackPane(fileIcon, extension);
    }

    private String fileExtension(String name) {
        if (name == null) return "FILE";
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot >= name.length() - 1) return "FILE";
        String extension = name.substring(dot + 1).toUpperCase();
        return extension.length() > 4 ? extension.substring(0, 4) : extension;
    }

    private HBox createContentRow(
            HBox wrapper, Node initialContent, ChatViewModel.MessageItem item) {
        HBox row = new HBox(6);
        row.setAlignment(item.isMe() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        Node[] content = {initialContent};
        Node[] more = {null};

        if (item.isDeleted() || item.isDeletedForMe()) {
            content[0] = deletedLabel(item);
            row.getChildren().add(content[0]);
        } else {
            attachMenu(wrapper, row, content, more, item);
        }

        item.isDeletedProperty().addListener((obs, oldValue, deleted) -> {
            if (deleted) Platform.runLater(() -> replaceWithDeleted(row, content, more, item));
        });
        item.isDeletedForMeProperty().addListener((obs, oldValue, deleted) -> {
            if (deleted) Platform.runLater(() -> replaceWithDeleted(row, content, more, item));
        });
        return row;
    }

    private void attachMenu(
            HBox wrapper,
            HBox row,
            Node[] content,
            Node[] more,
            ChatViewModel.MessageItem item) {
        Node contentNode = content[0];
        contentNode.setOnContextMenuRequested(event -> {
            ContextMenu menu = menus.create(contentNode, item);
            if (!menu.getItems().isEmpty()) {
                menu.show(contentNode, event.getScreenX(), event.getScreenY());
            }
        });
        Button button = iconButton("fa-ellipsis-v", "Tùy chọn tin nhắn");
        button.getStyleClass().add("message-more-button");
        button.setOnAction(event -> {
            ContextMenu menu = menus.create(contentNode, item);
            if (!menu.getItems().isEmpty()) {
                menu.show(button, javafx.geometry.Side.BOTTOM, 0, 0);
            }
        });
        Button react = iconButton("fa-smile-o", "Thả cảm xúc");
        react.getStyleClass().add("message-reaction-button");
        Runnable refreshReactButton = () -> {
            boolean visible = hasPersistedId(item);
            react.setVisible(visible);
            react.setManaged(visible);
        };
        item.statusProperty().addListener((obs, oldValue, newValue) -> refreshReactButton.run());
        refreshReactButton.run();
        react.setOnAction(event -> EmojiPicker.showReactionPicker(
                react, Side.BOTTOM, emoji -> viewModel.reactToMessage(item, emoji)));
        HBox actions = new HBox(2, react, button);
        actions.setAlignment(Pos.CENTER);
        more[0] = actions;
        if (item.isMe()) row.getChildren().addAll(actions, contentNode);
        else row.getChildren().addAll(contentNode, actions);
    }

    private Button iconButton(String iconLiteral, String tooltip) {
        FontIcon icon = new FontIcon(iconLiteral);
        Button button = new Button();
        button.setGraphic(icon);
        button.setTooltip(new Tooltip(tooltip));
        button.setFocusTraversable(false);
        return button;
    }

    private void replaceWithDeleted(
            HBox row, Node[] content, Node[] more, ChatViewModel.MessageItem item) {
        if (more[0] != null) {
            row.getChildren().remove(more[0]);
            more[0] = null;
        }
        int index = row.getChildren().indexOf(content[0]);
        if (index >= 0) {
            content[0] = deletedLabel(item);
            row.getChildren().set(index, content[0]);
        }
    }

    private Label deletedLabel(ChatViewModel.MessageItem item) {
        String text;
        if (item.isDeleted()) {
            text = item.isMe() ? "Bạn đã thu hồi tin nhắn này" : "Tin nhắn đã bị thu hồi";
        } else {
            text = "Bạn đã xóa tin nhắn này";
        }
        Label label = new Label(text);
        label.getStyleClass().addAll(
                "chat-message-bubble", item.isMe() ? "my-message" : "other-message");
        label.setStyle("-fx-font-style: italic; -fx-text-fill: "
                + (item.isMe() ? "white" : "gray") + ";");
        return label;
    }

    private Label timeLabel(ChatViewModel.MessageItem item) {
        Label label = new Label(item.getTime());
        label.setStyle("-fx-font-size: 10px; -fx-text-fill: #9ca3af;");
        return label;
    }

    private FlowPane reactionBar(ChatViewModel.MessageItem item) {
        FlowPane bar = new FlowPane(5, 4);
        bar.getStyleClass().add("message-reactions");
        bar.setAlignment(item.isMe() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        Runnable refresh = () -> {
            Map<String, Integer> counts = new LinkedHashMap<>();
            item.getReactions().values().forEach(
                    emoji -> counts.merge(emoji, 1, Integer::sum));
            bar.getChildren().clear();
            counts.forEach((emoji, count) -> {
                Button chip = new Button();
                chip.getStyleClass().add("message-reaction-chip");
                HBox content = new HBox(3, EmojiPicker.emojiGraphic(emoji, 15),
                        new Label(String.valueOf(count)));
                content.setAlignment(Pos.CENTER);
                chip.setGraphic(content);
                chip.setOnAction(event -> viewModel.reactToMessage(item, emoji));
                bar.getChildren().add(chip);
            });
            boolean visible = !item.isDeleted() && !item.isDeletedForMe() && !counts.isEmpty();
            bar.setVisible(visible);
            bar.setManaged(visible);
        };
        item.getReactions().addListener(
                (MapChangeListener<String, String>) change -> refresh.run());
        item.isDeletedProperty().addListener((obs, oldValue, newValue) -> refresh.run());
        item.isDeletedForMeProperty().addListener((obs, oldValue, newValue) -> refresh.run());
        refresh.run();
        return bar;
    }

    private Label statusLabel(ChatViewModel.MessageItem item) {
        Label label = new Label();
        label.getStyleClass().add("message-status");
        Runnable refresh = () -> {
            String text = switch (item.getStatus()) {
                case "SENDING" -> "Đang gửi";
                case "FAILED" -> "Gửi lỗi";
                case "SEEN" -> "Đã xem";
                case "DELIVERED" -> "Đã nhận";
                default -> "Đã gửi";
            };
            label.getStyleClass().removeAll(
                    "message-status-sending", "message-status-sent",
                    "message-status-seen", "message-status-failed");
            label.getStyleClass().add(switch (item.getStatus()) {
                case "SENDING" -> "message-status-sending";
                case "FAILED" -> "message-status-failed";
                case "SEEN" -> "message-status-seen";
                default -> "message-status-sent";
            });
            if (item.isStarred()) text += "  ★";
            if (item.isPinned()) text += "  📌";
            label.setText(text);
        };
        item.statusProperty().addListener((obs, oldValue, newValue) -> refresh.run());
        item.starredProperty().addListener((obs, oldValue, newValue) -> refresh.run());
        item.pinnedProperty().addListener((obs, oldValue, newValue) -> refresh.run());
        refresh.run();
        return label;
    }

    private StackPane uploadProgress(ChatViewModel.MessageItem item) {
        ProgressIndicator progress = new ProgressIndicator(0);
        progress.setPrefSize(38, 38);
        progress.getStyleClass().add("file-upload-progress");
        Label percent = new Label("0%");
        percent.getStyleClass().add("file-upload-percent");
        StackPane pane = new StackPane(progress, percent);
        pane.getStyleClass().add("file-upload-progress-wrap");
        Runnable refresh = () -> {
            boolean uploading = item.isFile() && "SENDING".equals(item.getStatus());
            pane.setVisible(uploading);
            pane.setManaged(uploading);
            double value = Math.max(0, item.getUploadProgress());
            progress.setProgress(value);
            percent.setText(Math.round(value * 100) + "%");
        };
        item.uploadProgressProperty().addListener((obs, oldValue, newValue) -> refresh.run());
        item.statusProperty().addListener((obs, oldValue, newValue) -> refresh.run());
        refresh.run();
        return pane;
    }

    private void attachDownload(Node node, ChatViewModel.MessageItem item) {
        if (!hasPersistedId(item)) return;
        node.setOnMouseClicked(event -> {
            if (event.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;
            String fileName = item.getResponse().getFileName();
            String fileSize = item.getResponse().getFileSize() == null
                    ? "Không xác định" : FileUtils.formatFileSize(item.getResponse().getFileSize());
            dialogs.showDownload(node.getScene().getWindow(), fileName, fileSize,
                    () -> download(node, item, fileName));
        });
    }

    private void download(Node owner, ChatViewModel.MessageItem item, String fileName) {
        try {
            byte[] data = viewModel.downloadFile(item.getResponse());
            File saveFile = fileDialogs.chooseDownload(owner.getScene().getWindow(), fileName);
            if (saveFile == null) return;
            Files.write(saveFile.toPath(), data);
            notification.accept(
                    "Thành công", "Đã tải file thành công: " + saveFile.getAbsolutePath());
        } catch (Exception e) {
            notification.accept("Lỗi", "Không thể tải file: " + e.getMessage());
        }
    }

    private boolean hasPersistedId(ChatViewModel.MessageItem item) {
        return item.getResponse() != null
                && item.getResponse().getId() != null
                && !item.getResponse().getId().startsWith("pending-");
    }

    private void highlight(Node node) {
        if (!node.getStyleClass().contains("message-search-match")) {
            node.getStyleClass().add("message-search-match");
        }
        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(event -> node.getStyleClass().remove("message-search-match"));
        pause.play();
    }

    private void centerOn(Node row) {
        double contentHeight = container.getHeight();
        double viewportHeight = scrollPane.getViewportBounds().getHeight();
        if (contentHeight <= viewportHeight) return;
        double middle = row.getBoundsInParent().getMinY()
                + row.getBoundsInParent().getHeight() / 2;
        double target = (middle - viewportHeight / 2) / (contentHeight - viewportHeight);
        scrollPane.setVvalue(Math.max(0, Math.min(1, target)));
    }
}
