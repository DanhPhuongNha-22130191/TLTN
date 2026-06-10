package secretchat.chat.view;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import secretchat.chat.viewmodel.ChatViewModel;

public final class MessageContextMenuFactory {
    private final ChatViewModel viewModel;
    private final ChatDialogService dialogs;

    public MessageContextMenuFactory(ChatViewModel viewModel, ChatDialogService dialogs) {
        this.viewModel = viewModel;
        this.dialogs = dialogs;
    }

    public ContextMenu create(Node owner, ChatViewModel.MessageItem item) {
        ContextMenu menu = new ContextMenu();
        if (!item.isFile()) menu.getItems().add(copyItem(item));
        if (!hasPersistedId(item)) return menu;

        if (item.isMe() && !item.isFile()) menu.getItems().add(editItem(item));
        menu.getItems().add(starItem(item));
        menu.getItems().add(pinItem(item));
        menu.getItems().add(deleteItem(owner, item));
        if (item.isMe()) menu.getItems().add(recallItem(owner, item));
        return menu;
    }

    private MenuItem copyItem(ChatViewModel.MessageItem item) {
        MenuItem copy = new MenuItem("Sao chép");
        copy.setOnAction(event -> {
            String text = item.getContent();
            if (text == null || text.isEmpty()) return;
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            Clipboard.getSystemClipboard().setContent(content);
        });
        return copy;
    }

    private MenuItem editItem(ChatViewModel.MessageItem item) {
        MenuItem edit = new MenuItem("Chỉnh sửa");
        edit.setOnAction(event -> {
            TextInputDialog dialog = new TextInputDialog(item.getContent());
            dialog.setTitle("Chỉnh sửa tin nhắn");
            dialog.setHeaderText(null);
            dialog.setContentText("Nội dung:");
            dialog.showAndWait().filter(text -> !text.isBlank())
                    .ifPresent(text -> viewModel.editMessage(item, text));
        });
        return edit;
    }

    private MenuItem starItem(ChatViewModel.MessageItem item) {
        MenuItem star = new MenuItem(item.isStarred() ? "Bỏ đánh dấu sao" : "Đánh dấu sao");
        star.setOnAction(event -> viewModel.toggleStar(item));
        return star;
    }

    private MenuItem pinItem(ChatViewModel.MessageItem item) {
        MenuItem pin = new MenuItem(item.isPinned() ? "Bỏ ghim" : "Ghim tin nhắn");
        pin.setOnAction(event -> viewModel.togglePin(item));
        item.pinnedProperty().addListener(
                (obs, oldValue, pinned) -> pin.setText(pinned ? "Bỏ ghim" : "Ghim tin nhắn"));
        return pin;
    }

    private MenuItem deleteItem(Node owner, ChatViewModel.MessageItem item) {
        MenuItem delete = new MenuItem("Xóa");
        delete.setOnAction(event -> dialogs.showMessageAction(
                owner.getScene().getWindow(),
                "Xác nhận xóa",
                "Bạn có chắc chắn muốn xóa tin nhắn này không? "
                        + "Tin nhắn sẽ chỉ bị xóa ở phía bạn.",
                "Xóa",
                () -> viewModel.deleteMessageForUser(item.getResponse(), item)));
        return delete;
    }

    private MenuItem recallItem(Node owner, ChatViewModel.MessageItem item) {
        MenuItem recall = new MenuItem("Thu hồi");
        recall.setOnAction(event -> dialogs.showMessageAction(
                owner.getScene().getWindow(),
                "Xác nhận thu hồi",
                "Bạn có chắc chắn muốn thu hồi tin nhắn này không? "
                        + "Hành động này sẽ thu hồi với tất cả mọi người.",
                "Thu hồi",
                () -> viewModel.recallMessage(item.getResponse(), item)));
        return recall;
    }

    private boolean hasPersistedId(ChatViewModel.MessageItem item) {
        return item.getResponse() != null
                && item.getResponse().getId() != null
                && !item.getResponse().getId().startsWith("pending-");
    }
}
