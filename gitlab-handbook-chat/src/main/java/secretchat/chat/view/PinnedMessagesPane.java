package secretchat.chat.view;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import secretchat.chat.viewmodel.ChatViewModel;

import java.util.function.Consumer;

public final class PinnedMessagesPane {
    private final ChatViewModel viewModel;
    private final VBox area;
    private final StackPane contentPane;
    private final ListView<ChatViewModel.PinnedMessageItem> list;
    private final Label title;
    private final Label empty;
    private final Button collapseButton;
    private final Consumer<ChatViewModel.MessageItem> scrollToMessage;
    private final PauseTransition collapsePause = new PauseTransition(Duration.millis(120));

    private boolean collapsed = true;
    private boolean expandedByUser;
    private boolean contextMenuOpen;

    public PinnedMessagesPane(
            ChatViewModel viewModel,
            VBox area,
            StackPane contentPane,
            ListView<ChatViewModel.PinnedMessageItem> list,
            Label title,
            Label empty,
            Button collapseButton,
            Consumer<ChatViewModel.MessageItem> scrollToMessage) {
        this.viewModel = viewModel;
        this.area = area;
        this.contentPane = contentPane;
        this.list = list;
        this.title = title;
        this.empty = empty;
        this.collapseButton = collapseButton;
        this.scrollToMessage = scrollToMessage;
    }

    public void initialize() {
        list.setItems(viewModel.getPinnedMessageList());
        list.setCellFactory(ignored -> createCell());
        collapsePause.setOnFinished(event -> collapseWhenPointerLeaves());
        area.hoverProperty().addListener((obs, oldValue, hovered) -> handleHover(hovered));
        contentPane.hoverProperty().addListener((obs, oldValue, hovered) -> handleHover(hovered));
        refresh();
    }

    public void resetForConversation() {
        collapsed = true;
        expandedByUser = false;
        refresh();
    }

    public void toggle() {
        collapsed = !collapsed;
        expandedByUser = !collapsed;
        refresh();
    }

    public void collapse() {
        collapsed = true;
        expandedByUser = false;
        refresh();
    }

    public void refresh() {
        boolean hasConversation = viewModel.activeConversationProperty().get() != null;
        int count = viewModel.getPinnedMessageList().size();
        boolean visible = hasConversation && count > 0;
        area.setVisible(visible);
        area.setManaged(visible);
        title.setText("Danh sách ghim (" + count + ")");
        contentPane.setVisible(visible && !collapsed);
        contentPane.setManaged(visible && !collapsed);
        list.setVisible(count > 0);
        list.setManaged(count > 0);
        empty.setVisible(count == 0);
        empty.setManaged(count == 0);
        collapseButton.setText(collapsed ? "Mở rộng" : "Thu gọn");
        if (visible && !collapsed) {
            Platform.runLater(() -> {
                list.refresh();
                contentPane.applyCss();
                contentPane.layout();
                area.requestLayout();
            });
        }
    }

    private ListCell<ChatViewModel.PinnedMessageItem> createCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(ChatViewModel.PinnedMessageItem item, boolean emptyCell) {
                super.updateItem(item, emptyCell);
                setGraphic(emptyCell || item == null ? null : createRow(item));
            }
        };
    }

    private HBox createRow(ChatViewModel.PinnedMessageItem item) {
        Label preview = new Label(item.preview());
        preview.setMaxWidth(520);
        preview.setTextOverrun(OverrunStyle.ELLIPSIS);
        preview.getStyleClass().add("pinned-message-content");

        String metaText = item.sender();
        if (item.time() != null && !item.time().isBlank()) metaText += " • " + item.time();
        Label meta = new Label(metaText);
        meta.getStyleClass().add("pinned-message-meta");
        VBox text = new VBox(2, preview, meta);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button more = createMoreButton(item);
        HBox row = new HBox(10, typeIcon(item.type()), text, more);
        row.getStyleClass().add("pinned-message-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setOnMouseClicked(event -> {
            if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY
                    && event.getClickCount() == 1) goTo(item);
        });
        return row;
    }

    private Button createMoreButton(ChatViewModel.PinnedMessageItem item) {
        Button more = new Button();
        more.setGraphic(new FontIcon("fa-ellipsis-h"));
        more.getStyleClass().add("pinned-more-button");
        ContextMenu menu = new ContextMenu();
        MenuItem goTo = new MenuItem("Đi tới tin nhắn gốc");
        goTo.setOnAction(event -> {
            goTo(item);
            collapse();
        });
        MenuItem unpin = new MenuItem("Bỏ ghim");
        unpin.setOnAction(event -> {
            viewModel.unpinMessage(item);
            collapse();
        });
        menu.getItems().addAll(goTo, unpin);
        menu.setOnShowing(event -> {
            contextMenuOpen = true;
            collapsePause.stop();
            Platform.runLater(() -> {
                if (menu.getScene() != null && menu.getScene().getRoot() != null) {
                    menu.getScene().getRoot().setOnMouseExited(e -> {
                        menu.hide();
                        collapse();
                    });
                }
            });
        });
        menu.setOnHidden(event -> {
            contextMenuOpen = false;
            if (!area.isHover() && !contentPane.isHover()) collapsePause.playFromStart();
        });
        more.setOnMouseClicked(event -> event.consume());
        more.setOnAction(event -> {
            menu.show(more, javafx.geometry.Side.BOTTOM, 0, 0);
            if (menu.getScene() != null) {
                menu.getScene().getStylesheets().add(
                        getClass().getResource("/css/chat.css").toExternalForm());
            }
            event.consume();
        });
        return more;
    }

    private StackPane typeIcon(String type) {
        String literal = switch (type) {
            case "IMAGE" -> "fa-picture-o";
            case "FILE", "VIDEO" -> "fa-file-o";
            case "LINK" -> "fa-link";
            default -> "fa-comment-o";
        };
        FontIcon icon = new FontIcon(literal);
        icon.getStyleClass().add("pinned-type-icon");
        StackPane wrapper = new StackPane(icon);
        wrapper.getStyleClass().add("pinned-type-icon-wrap");
        return wrapper;
    }

    private void goTo(ChatViewModel.PinnedMessageItem pinned) {
        viewModel.ensureMessageLoaded(pinned.messageId())
                .thenAccept(item -> {
                    if (item != null) Platform.runLater(() -> scrollToMessage.accept(item));
                });
    }

    private void handleHover(boolean hovered) {
        if (hovered) collapsePause.stop();
        else if (!contextMenuOpen) collapsePause.playFromStart();
    }

    private void collapseWhenPointerLeaves() {
        if (!area.isHover() && !contentPane.isHover()
                && !contextMenuOpen && expandedByUser && !collapsed) {
            collapse();
        }
    }
}
