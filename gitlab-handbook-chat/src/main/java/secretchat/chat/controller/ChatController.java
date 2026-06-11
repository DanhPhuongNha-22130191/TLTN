package secretchat.chat.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.geometry.Side;
import javafx.stage.Stage;
import secretchat.chat.viewmodel.ChatViewModel;
import secretchat.chat.viewmodel.ChatViewModelFactory;
import secretchat.chat.viewmodel.MainViewModel;
import secretchat.chat.view.ChatDialogService;
import secretchat.chat.view.ChatFileDialogs;
import secretchat.chat.view.ChatLifecycleHandler;
import secretchat.chat.view.ChatMessagePane;
import secretchat.chat.view.ChatNotificationHandler;
import secretchat.chat.view.ChatSearchHandler;
import secretchat.chat.view.ChatViewBindings;
import secretchat.chat.view.ConversationCellFactory;
import secretchat.chat.view.ConversationDetailsDialog;
import secretchat.chat.view.ConversationNavigator;
import secretchat.chat.view.FilePreviewController;
import secretchat.chat.view.EmojiPicker;
import secretchat.chat.view.MessageContextMenuFactory;
import secretchat.chat.view.PinnedMessagesPane;
import secretchat.chat.view.NewMessageNotifier;
import secretchat.chat.view.TypingIndicatorController;

public class ChatController extends BaseChatController {

    @FXML private TextField searchField;
    @FXML private ListView<String> privateChatList;
    @FXML private ListView<String> groupChatList;
    @FXML private TabPane chatTabPane;
    @FXML private HBox chatHeaderInfoContainer;
    @FXML private Label chatTitleLabel;
    @FXML private Label chatStatusLabel;
    @FXML private ScrollPane messageScrollPane;
    @FXML private VBox messageContainer;
    @FXML private TextField messageInput;
    @FXML private HBox filePreviewBox;
    @FXML private Label fileNameLabel;
    @FXML private Label fileSizeLabel;
    @FXML private VBox chatActionsPanel;
    @FXML private Button groupMembersButton;
    @FXML private TextField messageSearchField;
    @FXML private ListView<ChatViewModel.PinnedMessageItem> pinnedMessageList;
    @FXML private VBox pinnedArea;
    @FXML private StackPane pinnedContent;
    @FXML private Label pinnedTitleLabel;
    @FXML private Label pinnedEmptyLabel;
    @FXML private Button pinnedCollapseButton;
    @FXML private Label typingLabel;
    @FXML private ProgressIndicator aiProgressIndicator;
    @FXML private Label aiLoadingLabel;
    @FXML private Button scrollBottomButton;
    @FXML private Label searchResultLabel;
    @FXML private Button emojiButton;
    @FXML private BorderPane chatArea;
    @FXML private VBox chatContentArea;
    @FXML private VBox inputArea;

    private final ChatViewModel viewModel;
    private final NewMessageNotifier newMessageNotifier = new NewMessageNotifier();
    private final ChatDialogService dialogs = new ChatDialogService(ChatController.class);
    private final ChatFileDialogs fileDialogs = new ChatFileDialogs();
    private MessageContextMenuFactory messageMenus;
    private ChatMessagePane messagePane;
    private PinnedMessagesPane pinnedPane;
    private ConversationNavigator conversations;
    private TypingIndicatorController typingIndicator;
    private FilePreviewController filePreview;
    private ChatSearchHandler searchHandler;
    private ChatNotificationHandler notificationHandler;
    private ChatLifecycleHandler lifecycle;
    private ChatViewBindings viewBindings;

    public ChatController() {
        this(ChatViewModelFactory.create());
    }

    public ChatController(ChatViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @FXML
    public void initialize() {
        messageMenus = new MessageContextMenuFactory(viewModel, dialogs);
        messagePane = new ChatMessagePane(
                viewModel, messageContainer, messageScrollPane, messageMenus,
                dialogs, fileDialogs, this::showAlert);
        pinnedPane = new PinnedMessagesPane(
                viewModel, pinnedArea, pinnedContent, pinnedMessageList,
                pinnedTitleLabel, pinnedEmptyLabel, pinnedCollapseButton,
                messagePane::scrollTo);
        conversations = new ConversationNavigator(
                viewModel, privateChatList, groupChatList, chatTabPane,
                chatTitleLabel, chatStatusLabel);
        typingIndicator = new TypingIndicatorController(viewModel, typingLabel, messageInput);
        filePreview = new FilePreviewController(
                messageInput, filePreviewBox, fileNameLabel, fileSizeLabel,
                fileDialogs, this::showAlert);
        searchHandler = new ChatSearchHandler(
                viewModel, messagePane, messageSearchField, searchResultLabel);
        notificationHandler = new ChatNotificationHandler(
                viewModel, conversations, newMessageNotifier, this::ownerStage, this::showAlert);
        viewBindings = new ChatViewBindings(
                viewModel, privateChatList, groupChatList, chatTabPane,
                messageScrollPane, scrollBottomButton, chatActionsPanel, groupMembersButton,
                aiProgressIndicator, aiLoadingLabel, chatContentArea, inputArea,
                messagePane, pinnedPane, conversations, searchHandler, this::clearConversationUI);
        lifecycle = new ChatLifecycleHandler(
                viewModel, messageInput, typingIndicator, notificationHandler, viewBindings);
        viewModel.sessionExpiredProperty().addListener((observable, oldValue, expired) -> {
            if (expired) {
                javafx.application.Platform.runLater(this::handleSessionExpired);
            }
        });

        typingIndicator.initialize();
        viewBindings.initialize();
        notificationHandler.initialize();

        setupCellFactories();
        setupFileDrop();
        viewModel.init();
        chatHeaderInfoContainer.setOnMouseClicked(event -> handleOpenActiveChatProfile());
        lifecycle.initialize();
    }

    private void handleSessionExpired() {
        lifecycle.close();
        new MainViewModel().logout();
        Stage stage = ownerStage();
        if (stage != null) {
            switchScene(stage, "/fxml/login-view.fxml");
        }
    }

    private void setupFileDrop() {
        chatArea.setOnDragOver(this::handleFileDragOver);
        chatArea.setOnDragDropped(this::handleFileDrop);
    }

    private void handleFileDragOver(DragEvent event) {
        if (event.getGestureSource() != chatArea
                && event.getDragboard().hasFiles()
                && event.getDragboard().getFiles().stream().anyMatch(java.io.File::isFile)) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    private void handleFileDrop(DragEvent event) {
        boolean sent = false;
        if (event.getDragboard().hasFiles()) {
            for (java.io.File file : event.getDragboard().getFiles()) {
                if (!file.isFile()) {
                    continue;
                }
                if (fileDialogs.exceedsUploadLimit(file)) {
                    showAlert("Lỗi", "File " + file.getName()
                            + " vượt quá kích thước tối đa 100 MB.");
                    continue;
                }
                viewModel.sendMessage("", file);
                sent = true;
            }
        }
        event.setDropCompleted(sent);
        event.consume();
    }

    private void setupCellFactories() {
        var chatListCellFactory = ConversationCellFactory.create(
                viewModel,
                privateChatList,
                groupChatList,
                this::showUserProfile,
                this::confirmRemoveFriend);
        privateChatList.setCellFactory(chatListCellFactory);
        groupChatList.setCellFactory(chatListCellFactory);
    }

    private void showUserProfile(String displayName) {
        secretchat.dto.response.UserResponse user = viewModel.getUserByName(displayName);
        if (user != null) {
            ConversationDetailsDialog.showUserProfile(
                    viewModel, messageInput.getScene().getWindow(), user);
        }
    }

    @FXML
    private void handleTogglePinned() {
        pinnedPane.toggle();
    }

    private void confirmRemoveFriend(String displayName) {
        if (!dialogs.confirm(
                "Xóa bạn bè",
                "Xóa " + displayName + " khỏi danh sách bạn bè?",
                "Lịch sử tin nhắn sẽ được giữ lại.")) return;

        boolean wasCurrentChat = displayName.equals(viewModel.currentChatNameProperty().get());
        viewModel.removeFriend(displayName);
        if (wasCurrentChat) {
            conversations.resetHeader();
        }
    }

    private void handleOpenActiveChatProfile() {
        if (conversations.isActivePrivateChat()) {
            secretchat.dto.response.UserResponse user =
                    viewModel.getUserByName(conversations.activeTitle());
            if (user != null) {
                ConversationDetailsDialog.showUserProfile(
                        viewModel, messageInput.getScene().getWindow(), user);
            }
        }
    }

    private void clearConversationUI() {
        // Clear all UI elements when no conversation is selected
        chatTitleLabel.setText("Chọn cuộc trò chuyện");
        chatStatusLabel.setText("Cá nhân / Nhóm");
        messageContainer.getChildren().clear();
        messageInput.clear();
        filePreview.clear();
        searchHandler.reset();
        pinnedMessageList.getItems().clear();
        viewModel.clearConversationData();
    }

    @FXML private void handleShowGroupMembers() {
        ConversationDetailsDialog.showMembers(viewModel, messageInput.getScene().getWindow(),
                this::openPrivateChatFromMemberProfile);
    }

    @FXML private void handleShowSentFiles() {
        ConversationDetailsDialog.showFiles(viewModel, messageInput.getScene().getWindow());
    }

    @FXML private void handleShowSentLinks() {
        ConversationDetailsDialog.showLinks(viewModel, messageInput.getScene().getWindow());
    }

    @FXML
    private void handleSelectPrivateChat() {
        conversations.selectPrivateFromList();
    }

    @FXML
    private void handleSelectAIAssistant() {
        conversations.selectAi();
    }

    @FXML
    private void handleSelectGroupChat() {
        conversations.selectGroupFromList();
    }

    @FXML
    private void handleSendMessage() {
        String text = messageInput.getText();
        viewModel.sendMessage(text, filePreview.selectedFile());
        typingIndicator.stopLocalTyping();
        
        messageInput.clear();
        filePreview.clear();
    }

    @FXML
    private void handleShowEmojiPicker() {
        EmojiPicker.showMessagePicker(emojiButton, Side.TOP, emoji -> {
            int caret = messageInput.getCaretPosition();
            messageInput.insertText(caret, emoji);
            messageInput.requestFocus();
        });
    }

    @FXML
    private void handleSearchMessages() {
        searchHandler.searchMessages();
    }

    @FXML
    private void handleScrollToBottom() {
        messagePane.scrollBottom();
    }

    @FXML
    private void handleAttachFile() {
        filePreview.chooseFile(false);
    }

    @FXML
    private void handleRemoveFile() {
        filePreview.clear();
    }

    @FXML
    private void handleCreateGroup() {
        dialogs.show("/fxml/create-group-dialog.fxml", messageInput.getScene().getWindow(),
                (CreateGroupController controller) ->
                        controller.setOnGroupCreated(viewModel::createGroup));
    }

    @FXML
    private void handleAddFriend() {
        dialogs.show("/fxml/add-friend-dialog.fxml", messageInput.getScene().getWindow(),
                (AddFriendController controller) -> controller.setOnFriendAdded(viewModel::addFriend));
    }

    private void openPrivateChatFromMemberProfile(secretchat.dto.response.UserResponse profile) {
        conversations.openProfile(profile);
    }

    @FXML
    private void handleProfile() {
        dialogs.show("/fxml/profile-dialog.fxml", messageInput.getScene().getWindow(),
                (ProfileDialogController controller) -> controller.setViewModel(viewModel));
    }

    @FXML
    private void handleSearch() {
        searchHandler.searchConversations(searchField.getText());
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        lifecycle.close();
        new MainViewModel().logout();
        switchScene(event, "/fxml/login-view.fxml");
    }

    private void showAlert(String title, String content) {
        dialogs.showNotification(title, content);
    }

    private Stage ownerStage() {
        if (messageInput.getScene() == null
                || !(messageInput.getScene().getWindow() instanceof Stage stage)) {
            return null;
        }
        return stage;
    }
}
