package secretchat.chat.view;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import secretchat.chat.viewmodel.ChatViewModel;
import secretchat.dto.response.ConversationResponse;

public final class ChatViewBindings implements AutoCloseable {

    private final ChatViewModel viewModel;
    private final ListView<String> privateChats;
    private final ListView<String> groupChats;
    private final TabPane tabs;
    private final ScrollPane messageScroll;
    private final Button scrollBottomButton;
    private final VBox actionsPanel;
    private final Button groupMembersButton;
    private final ProgressIndicator aiProgress;
    private final Label aiLoadingLabel;
    private final VBox chatContent;
    private final VBox inputArea;
    private final ChatMessagePane messagePane;
    private final PinnedMessagesPane pinnedPane;
    private final ConversationNavigator conversations;
    private final ChatSearchHandler searchHandler;
    private final Runnable clearConversation;

    private final ListChangeListener<String> privateChatsListener;
    private final ListChangeListener<String> groupChatsListener;
    private final ListChangeListener<ChatViewModel.MessageItem> messagesListener;
    private final ListChangeListener<ChatViewModel.PinnedMessageItem> pinnedListener;
    private final ChangeListener<Boolean> groupStateListener;
    private final ChangeListener<ConversationResponse> conversationListener;
    private final ChangeListener<Number> scrollListener;
    private final ChangeListener<Number> conversationVersionListener;
    private final ChangeListener<Tab> tabListener;

    public ChatViewBindings(
            ChatViewModel viewModel,
            ListView<String> privateChats,
            ListView<String> groupChats,
            TabPane tabs,
            ScrollPane messageScroll,
            Button scrollBottomButton,
            VBox actionsPanel,
            Button groupMembersButton,
            ProgressIndicator aiProgress,
            Label aiLoadingLabel,
            VBox chatContent,
            VBox inputArea,
            ChatMessagePane messagePane,
            PinnedMessagesPane pinnedPane,
            ConversationNavigator conversations,
            ChatSearchHandler searchHandler,
            Runnable clearConversation) {
        this.viewModel = viewModel;
        this.privateChats = privateChats;
        this.groupChats = groupChats;
        this.tabs = tabs;
        this.messageScroll = messageScroll;
        this.scrollBottomButton = scrollBottomButton;
        this.actionsPanel = actionsPanel;
        this.groupMembersButton = groupMembersButton;
        this.aiProgress = aiProgress;
        this.aiLoadingLabel = aiLoadingLabel;
        this.chatContent = chatContent;
        this.inputArea = inputArea;
        this.messagePane = messagePane;
        this.pinnedPane = pinnedPane;
        this.conversations = conversations;
        this.searchHandler = searchHandler;
        this.clearConversation = clearConversation;

        privateChatsListener = change -> Platform.runLater(this::selectDefaultPrivate);
        groupChatsListener = change -> Platform.runLater(this::selectDefaultGroup);
        messagesListener = this::handleMessagesChanged;
        pinnedListener = change -> pinnedPane.refresh();
        groupStateListener = (observable, oldValue, isGroup) -> updateRightPanel(isGroup);
        conversationListener = (observable, oldValue, conversation) ->
                handleConversationChanged(oldValue, conversation);
        scrollListener = (observable, oldValue, value) -> updateScrollButton(value.doubleValue());
        conversationVersionListener = (observable, oldValue, value) -> refreshConversationLists();
        tabListener = (observable, oldTab, newTab) -> handleTabChanged(oldTab, newTab);
    }

    public void initialize() {
        privateChats.setItems(viewModel.getPrivateChatList());
        groupChats.setItems(viewModel.getGroupChatList());
        privateChats.getItems().addListener(privateChatsListener);
        groupChats.getItems().addListener(groupChatsListener);
        Platform.runLater(this::selectDefaultPrivate);
        Platform.runLater(this::selectDefaultGroup);

        pinnedPane.initialize();
        viewModel.getPinnedMessageList().addListener(pinnedListener);
        viewModel.getMessages().addListener(messagesListener);
        viewModel.currentChatIsGroupProperty().addListener(groupStateListener);
        viewModel.activeConversationProperty().addListener(conversationListener);
        messageScroll.vvalueProperty().addListener(scrollListener);
        viewModel.conversationVersionProperty().addListener(conversationVersionListener);
        tabs.getSelectionModel().selectedItemProperty().addListener(tabListener);

        aiProgress.visibleProperty().bind(viewModel.aiLoadingProperty());
        aiProgress.managedProperty().bind(aiProgress.visibleProperty());
        aiLoadingLabel.visibleProperty().bind(viewModel.aiLoadingProperty());
        aiLoadingLabel.managedProperty().bind(aiLoadingLabel.visibleProperty());
        chatContent.visibleProperty().bind(viewModel.activeConversationProperty().isNotNull());
        chatContent.managedProperty().bind(viewModel.activeConversationProperty().isNotNull());
        inputArea.visibleProperty().bind(viewModel.activeConversationProperty().isNotNull());
        inputArea.managedProperty().bind(viewModel.activeConversationProperty().isNotNull());
        updateRightPanel(false);
    }

    private void selectDefaultPrivate() {
        if (viewModel.activeConversationProperty().get() == null && !privateChats.getItems().isEmpty()) {
            privateChats.getSelectionModel().select(0);
            conversations.selectPrivateFromList();
        }
    }

    private void selectDefaultGroup() {
        Tab selectedTab = tabs.getSelectionModel().getSelectedItem();
        boolean groupTab = selectedTab != null && "Nhóm".equals(selectedTab.getText());
        if (groupTab
                && viewModel.activeConversationProperty().get() == null
                && !groupChats.getItems().isEmpty()) {
            groupChats.getSelectionModel().select(0);
            conversations.selectGroupFromList();
        }
    }

    private void handleMessagesChanged(ListChangeListener.Change<? extends ChatViewModel.MessageItem> change) {
        boolean added = false;
        boolean rebuild = false;
        while (change.next()) {
            if (change.wasRemoved() || change.wasReplaced() || change.wasPermutated()) {
                rebuild = true;
            }
            if (change.wasAdded()) {
                added = true;
                if (!rebuild) {
                    change.getAddedSubList().forEach(messagePane::render);
                }
            }
        }
        if (rebuild) {
            messagePane.renderAll();
        }
        if (added) {
            messagePane.scrollBottom();
        }
    }

    private void handleConversationChanged(
            ConversationResponse oldConversation, ConversationResponse conversation) {
        if (conversation != null && conversation != oldConversation) {
            pinnedPane.resetForConversation();
        }
        updateRightPanel(viewModel.currentChatIsGroupProperty().get());
        pinnedPane.refresh();
        searchHandler.clearResult();
    }

    private void updateScrollButton(double value) {
        boolean visible = value < 0.97;
        scrollBottomButton.setVisible(visible);
        scrollBottomButton.setManaged(visible);
    }

    private void refreshConversationLists() {
        privateChats.refresh();
        groupChats.refresh();
    }

    private void handleTabChanged(Tab oldTab, Tab newTab) {
        boolean groupTab = newTab != null && "Nhóm".equals(newTab.getText());
        if (oldTab != null && oldTab != newTab) {
            conversations.restoreTab(groupTab, clearConversation);
        }
        updateRightPanel(groupTab);
    }

    private void updateRightPanel(boolean group) {
        boolean hasConversation = viewModel.activeConversationProperty().get() != null;
        actionsPanel.setVisible(hasConversation);
        actionsPanel.setManaged(hasConversation);
        groupMembersButton.setVisible(group);
        groupMembersButton.setManaged(group);
    }

    @Override
    public void close() {
        privateChats.getItems().removeListener(privateChatsListener);
        groupChats.getItems().removeListener(groupChatsListener);
        viewModel.getMessages().removeListener(messagesListener);
        viewModel.getPinnedMessageList().removeListener(pinnedListener);
        viewModel.currentChatIsGroupProperty().removeListener(groupStateListener);
        viewModel.activeConversationProperty().removeListener(conversationListener);
        messageScroll.vvalueProperty().removeListener(scrollListener);
        viewModel.conversationVersionProperty().removeListener(conversationVersionListener);
        tabs.getSelectionModel().selectedItemProperty().removeListener(tabListener);
        aiProgress.visibleProperty().unbind();
        aiProgress.managedProperty().unbind();
        aiLoadingLabel.visibleProperty().unbind();
        aiLoadingLabel.managedProperty().unbind();
        chatContent.visibleProperty().unbind();
        chatContent.managedProperty().unbind();
        inputArea.visibleProperty().unbind();
        inputArea.managedProperty().unbind();
    }
}
