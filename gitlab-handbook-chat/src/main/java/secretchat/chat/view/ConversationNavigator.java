package secretchat.chat.view;

import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import secretchat.chat.viewmodel.ChatViewModel;
import secretchat.dto.response.GroupResponse;
import secretchat.dto.response.UserResponse;

public final class ConversationNavigator {
    private static final String AI_NAME = "TRỢ LÝ AI";

    private final ChatViewModel viewModel;
    private final ListView<String> privateChats;
    private final ListView<String> groupChats;
    private final TabPane tabs;
    private final Label title;
    private final Label status;
    private String lastPrivateChat;
    private String lastGroupChat;

    public ConversationNavigator(
            ChatViewModel viewModel,
            ListView<String> privateChats,
            ListView<String> groupChats,
            TabPane tabs,
            Label title,
            Label status) {
        this.viewModel = viewModel;
        this.privateChats = privateChats;
        this.groupChats = groupChats;
        this.tabs = tabs;
        this.title = title;
        this.status = status;
    }

    public void selectPrivateFromList() {
        String name = privateChats.getSelectionModel().getSelectedItem();
        if (name == null) return;
        lastPrivateChat = name;
        setHeader(name, "Chat cá nhân");
        viewModel.selectPrivateChat(name);
        privateChats.refresh();
    }

    public void selectGroupFromList() {
        String name = groupChats.getSelectionModel().getSelectedItem();
        if (name == null) return;
        lastGroupChat = name;
        setGroupHeader(name);
        viewModel.selectGroupChat(name);
        groupChats.refresh();
    }

    public void selectAi() {
        setHeader(AI_NAME, "Trợ lý ảo thông minh");
        viewModel.selectPrivateChat(AI_NAME);
        privateChats.getSelectionModel().clearSelection();
    }

    public void open(ChatViewModel.NewMessageEvent event) {
        if (event.group()) {
            lastGroupChat = event.chatName();
            tabs.getSelectionModel().select(1);
            groupChats.getSelectionModel().select(event.chatName());
            setGroupHeader(event.chatName());
            viewModel.selectGroupChat(event.chatName());
            groupChats.refresh();
        } else {
            lastPrivateChat = event.chatName();
            tabs.getSelectionModel().select(0);
            privateChats.getSelectionModel().select(event.chatName());
            setHeader(event.chatName(), "Chat cá nhân");
            viewModel.selectPrivateChat(event.chatName());
            privateChats.refresh();
        }
    }

    public void openMember(String name) {
        lastPrivateChat = name;
        tabs.getSelectionModel().select(0);
        setHeader(name, "Chat cá nhân");
        viewModel.openPrivateChatForMember(name);
        privateChats.getSelectionModel().select(name);
    }

    public void openProfile(UserResponse profile) {
        if (profile == null) return;
        String name = profile.getUsername() == null || profile.getUsername().isBlank()
                ? profile.getFullName() : profile.getUsername();
        lastPrivateChat = name;
        tabs.getSelectionModel().select(0);
        setHeader(name, "Chat cá nhân");
        viewModel.openPrivateChatForProfile(profile);
        privateChats.getSelectionModel().select(name);
        privateChats.refresh();
    }

    public void restoreTab(boolean groupTab, Runnable clearConversation) {
        ListView<String> list = groupTab ? groupChats : privateChats;
        String last = groupTab ? lastGroupChat : lastPrivateChat;
        if (last != null && list.getItems().contains(last)) {
            list.getSelectionModel().select(last);
        } else if (!list.getItems().isEmpty()) {
            list.getSelectionModel().select(0);
        } else {
            clearConversation.run();
            list.getSelectionModel().clearSelection();
            return;
        }
        if (groupTab) selectGroupFromList();
        else selectPrivateFromList();
    }

    public boolean isActivePrivateChat() {
        var conversation = viewModel.activeConversationProperty().get();
        return conversation != null
                && !"GROUP".equalsIgnoreCase(conversation.getType())
                && !AI_NAME.equals(title.getText());
    }

    public String activeTitle() {
        return title.getText();
    }

    public void resetHeader() {
        setHeader("Chọn cuộc trò chuyện", "Cá nhân / Nhóm");
    }

    private void setGroupHeader(String name) {
        GroupResponse group = viewModel.getGroupByName(name);
        String description = group != null ? group.getDescription() : null;
        setHeader(name, description == null ? "Chat nhóm" : "Chat nhóm - " + description);
    }

    private void setHeader(String titleText, String statusText) {
        title.setText(titleText);
        status.setText(statusText);
    }
}
