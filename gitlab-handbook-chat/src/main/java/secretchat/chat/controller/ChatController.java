package secretchat.chat.controller;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import secretchat.chat.viewmodel.ChatViewModel;
import secretchat.chat.viewmodel.MainViewModel;
import secretchat.chat.view.ChatDialogService;
import secretchat.chat.view.ChatFileDialogs;
import secretchat.chat.view.ChatMessagePane;
import secretchat.chat.view.ConversationCellFactory;
import secretchat.chat.view.ConversationDetailsDialog;
import secretchat.chat.view.ConversationNavigator;
import secretchat.chat.view.MessageContextMenuFactory;
import secretchat.chat.view.PinnedMessagesPane;
import secretchat.chat.view.NewMessageNotifier;

import java.io.File;

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
    @FXML private Button aiAssistantButton;
    @FXML private VBox chatContentArea;
    @FXML private VBox inputArea;

    private static final System.Logger LOGGER = System.getLogger(ChatController.class.getName());
    private ChatViewModel viewModel;
    private File selectedFile;
    private final PauseTransition typingPause = new PauseTransition(Duration.millis(900));
    private final Timeline typingAnimation = new Timeline();
    private String typingBaseText = "Đang nhập";
    private final NewMessageNotifier newMessageNotifier = new NewMessageNotifier();
    private final ChatDialogService dialogs = new ChatDialogService(ChatController.class);
    private final ChatFileDialogs fileDialogs = new ChatFileDialogs();
    private MessageContextMenuFactory messageMenus;
    private ChatMessagePane messagePane;
    private PinnedMessagesPane pinnedPane;
    private ConversationNavigator conversations;

    @FXML
    public void initialize() {
        viewModel = new ChatViewModel();
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
        
        // Setup bindings
        privateChatList.setItems(viewModel.getPrivateChatList());
        Runnable selectDefault = () -> {
            if (viewModel.activeConversationProperty().get() == null && !privateChatList.getItems().isEmpty()) {
                privateChatList.getSelectionModel().select(0);
                handleSelectPrivateChat();
            }
        };
        privateChatList.getItems().addListener((ListChangeListener<String>) change -> {
            Platform.runLater(selectDefault);
        });
        Platform.runLater(selectDefault);
        groupChatList.setItems(viewModel.getGroupChatList());
        Runnable selectDefaultGroup = () -> {
            boolean isGroupTab = chatTabPane.getSelectionModel().getSelectedItem() != null 
                    && "Nhóm".equals(chatTabPane.getSelectionModel().getSelectedItem().getText());
            if (isGroupTab && viewModel.activeConversationProperty().get() == null && !groupChatList.getItems().isEmpty()) {
                groupChatList.getSelectionModel().select(0);
                handleSelectGroupChat();
            }
        };
        groupChatList.getItems().addListener((ListChangeListener<String>) change -> {
            Platform.runLater(selectDefaultGroup);
        });
        Platform.runLater(selectDefaultGroup);
        pinnedPane.initialize();
        viewModel.getPinnedMessageList().addListener(
                (ListChangeListener<ChatViewModel.PinnedMessageItem>) change -> pinnedPane.refresh());
        typingLabel.textProperty().bind(viewModel.typingTextProperty());
        typingLabel.visibleProperty().bind(viewModel.typingTextProperty().isNotNull());
        typingLabel.managedProperty().bind(typingLabel.visibleProperty());
        aiProgressIndicator.visibleProperty().bind(viewModel.aiLoadingProperty());
        aiProgressIndicator.managedProperty().bind(aiProgressIndicator.visibleProperty());
        aiLoadingLabel.visibleProperty().bind(viewModel.aiLoadingProperty());
        aiLoadingLabel.managedProperty().bind(aiLoadingLabel.visibleProperty());
        
        chatContentArea.visibleProperty().bind(viewModel.activeConversationProperty().isNotNull());
        chatContentArea.managedProperty().bind(viewModel.activeConversationProperty().isNotNull());
        inputArea.visibleProperty().bind(viewModel.activeConversationProperty().isNotNull());
        inputArea.managedProperty().bind(viewModel.activeConversationProperty().isNotNull());
        typingLabel.textProperty().unbind();
        typingAnimation.setCycleCount(Timeline.INDEFINITE);
        typingAnimation.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO, event -> typingLabel.setText(typingBaseText + ".")),
                new KeyFrame(Duration.millis(300), event -> typingLabel.setText(typingBaseText + "..")),
                new KeyFrame(Duration.millis(600), event -> typingLabel.setText(typingBaseText + "...")));
        viewModel.typingTextProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null || newValue.isBlank()) {
                typingAnimation.stop();
                typingLabel.setText("");
            } else {
                typingBaseText = newValue.replace("...", "");
                typingAnimation.playFromStart();
            }
        });

        // Listen for messages updates
        viewModel.getMessages().addListener((ListChangeListener<ChatViewModel.MessageItem>) change -> {
            boolean added = false;
            boolean rebuild = false;
            while (change.next()) {
                if (change.wasRemoved() || change.wasReplaced() || change.wasPermutated()) {
                    rebuild = true;
                }
                if (change.wasAdded()) {
                    added = true;
                    if (!rebuild) {
                        for (ChatViewModel.MessageItem item : change.getAddedSubList()) {
                            messagePane.render(item);
                        }
                    }
                }
            }
            if (rebuild) {
                messagePane.renderAll();
            }
            if (added) {
                messagePane.scrollBottom();
            }
        });

        viewModel.currentChatIsGroupProperty().addListener((obs, oldVal, newVal) -> {
            updateRightPanel(newVal);
        });
        viewModel.activeConversationProperty().addListener((obs, oldVal, newVal) ->
        {
            if (newVal != null && newVal != oldVal) pinnedPane.resetForConversation();
            updateRightPanel(viewModel.currentChatIsGroupProperty().get());
            pinnedPane.refresh();
            searchResultLabel.setVisible(false);
            searchResultLabel.setManaged(false);
        });

        viewModel.errorMessageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                showAlert("Lỗi", newVal);
                viewModel.errorMessageProperty().set(null); // clear
            }
        });

        viewModel.notificationMessageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                showAlert("Thông báo", newVal);
                viewModel.notificationMessageProperty().set(null); // clear
            }
        });

        // Cell factories
        setupCellFactories();

        // Auto scroll to bottom
        messageScrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            boolean awayFromBottom = newVal.doubleValue() < 0.97;
            scrollBottomButton.setVisible(awayFromBottom);
            scrollBottomButton.setManaged(awayFromBottom);
        });
        viewModel.conversationVersionProperty().addListener((obs, oldVal, newVal) -> {
            privateChatList.refresh();
            groupChatList.refresh();
        });
        viewModel.newMessageEventProperty().addListener((obs, oldValue, event) -> {
            if (event != null) handleNewMessageNotification(event);
        });

        messageInput.textProperty().addListener((obs, oldText, newText) -> {
            viewModel.sendTyping(newText != null && !newText.isBlank());
            typingPause.stop();
            typingPause.setOnFinished(event -> viewModel.sendTyping(false));
            typingPause.playFromStart();
        });

        chatTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            boolean isGroupTab = newTab != null && "Nhóm".equals(newTab.getText());
            if (oldTab != null && oldTab != newTab) {
                conversations.restoreTab(isGroupTab, this::clearConversationUI);
            }
            updateRightPanel(isGroupTab);
        });

        updateRightPanel(false);
        viewModel.init();
        chatHeaderInfoContainer.setOnMouseClicked(event -> handleOpenActiveChatProfile());

        Platform.runLater(() -> {
            if (messageInput.getScene() != null && messageInput.getScene().getWindow() != null) {
                Stage stage = (Stage) messageInput.getScene().getWindow();
                Runnable updateApplicationState = () ->
                        viewModel.setApplicationActive(stage.isShowing()
                                && !stage.isIconified() && stage.isFocused());
                stage.focusedProperty().addListener((obs, oldValue, newValue) ->
                        updateApplicationState.run());
                stage.iconifiedProperty().addListener((obs, oldValue, newValue) ->
                        updateApplicationState.run());
                updateApplicationState.run();
                stage.setOnHidden(event -> {
                    viewModel.close();
                    newMessageNotifier.close();
                });
            }
        });
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

    private void handleNewMessageNotification(ChatViewModel.NewMessageEvent event) {
        Stage stage = (Stage) messageInput.getScene().getWindow();
        boolean viewingConversation = viewModel.activeConversationProperty().get() != null
                && event.conversationId().equals(viewModel.activeConversationProperty().get().getId());
        if (stage.isFocused() && viewingConversation) return;

        newMessageNotifier.show(stage, event, () -> conversations.open(event));
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
        clearSelectedFile();
        messageSearchField.clear();
        searchResultLabel.setVisible(false);
        searchResultLabel.setManaged(false);
        pinnedMessageList.getItems().clear();
        viewModel.clearConversationData();
    }

    private void updateRightPanel(boolean isGroup) {
        boolean hasConversation = viewModel.activeConversationProperty().get() != null;
        chatActionsPanel.setVisible(hasConversation);
        chatActionsPanel.setManaged(hasConversation);
        groupMembersButton.setVisible(isGroup);
        groupMembersButton.setManaged(isGroup);
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

    private void openPrivateChatFromMemberDialog(String memberName) {
        conversations.openMember(memberName);
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
        viewModel.sendMessage(text, selectedFile);
        viewModel.sendTyping(false);
        
        messageInput.clear();
        clearSelectedFile();
    }

    @FXML
    private void handleSearchMessages() {
        if (messageSearchField.getText() == null || messageSearchField.getText().isBlank()) {
            searchResultLabel.setVisible(false);
            searchResultLabel.setManaged(false);
            return;
        }
        ChatViewModel.MessageItem result = viewModel.searchConversationMessage(messageSearchField.getText());
        if (result != null) {
            searchResultLabel.setText("Đã tìm thấy: " + result.getSenderName()
                    + (result.getTime().isBlank() ? "" : " • " + result.getTime()));
            searchResultLabel.getStyleClass().remove("search-result-error");
            searchResultLabel.setVisible(true);
            searchResultLabel.setManaged(true);
            messagePane.scrollTo(result);
        } else {
            searchResultLabel.setText("Không tìm thấy tin nhắn phù hợp");
            if (!searchResultLabel.getStyleClass().contains("search-result-error")) {
                searchResultLabel.getStyleClass().add("search-result-error");
            }
            searchResultLabel.setVisible(true);
            searchResultLabel.setManaged(true);
        }
    }

    @FXML
    private void handleScrollToBottom() {
        messagePane.scrollBottom();
    }

    @FXML
    private void handleAttachFile() {
        selectedFile = fileDialogs.chooseUpload(messageInput.getScene().getWindow(), false);
        validateAndShowSelectedFile();
    }

    @FXML
    private void handleAttachImage() {
        selectedFile = fileDialogs.chooseUpload(messageInput.getScene().getWindow(), true);
        validateAndShowSelectedFile();
    }

    private void validateAndShowSelectedFile() {
        if (selectedFile == null) return;
        if (fileDialogs.exceedsUploadLimit(selectedFile)) {
            showAlert("Lỗi", "Kích thước file không được vượt quá 100 MB.");
            selectedFile = null;
            clearSelectedFile();
            return;
        }
        showSelectedFile(selectedFile);
    }

    @FXML
    private void handleRemoveFile() {
        clearSelectedFile();
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
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) {
            viewModel.loadData();
            return;
        }
        viewModel.performSearch(keyword);
    }

    @FXML
    private void handleShowMembers() {
        if (viewModel.currentChatNameProperty().get() == null) {
            showAlert("Thông báo", "Vui lòng chọn cuộc trò chuyện trước.");
            return;
        }
        if (!viewModel.currentChatIsGroupProperty().get()) {
            showAlert("Thông báo", "Đây là chat cá nhân.");
            return;
        }
        ConversationDetailsDialog.showMembers(viewModel, messageInput.getScene().getWindow(),
                this::openPrivateChatFromMemberProfile);
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        viewModel.close();
        newMessageNotifier.close();
        new MainViewModel().logout();
        switchScene(event, "/fxml/login-view.fxml");
    }

    @FXML
    private void handleChatOptions() {
        if (viewModel.currentChatNameProperty().get() == null) {
            showAlert("Thông báo", "Vui lòng chọn cuộc trò chuyện trước.");
            return;
        }
        showAlert("Tùy chọn", "Tùy chọn cuộc trò chuyện: " + viewModel.currentChatNameProperty().get());
    }

    @FXML
    private void handleAddMember() {
        if (!viewModel.currentChatIsGroupProperty().get() || viewModel.activeConversationProperty().get() == null) return;

        ChoiceDialog<String> dialog = new ChoiceDialog<>();
        dialog.setTitle("Thêm thành viên");
        dialog.setHeaderText("Chọn thành viên muốn thêm vào nhóm");
        dialog.setContentText("Thành viên:");

        dialog.getItems().addAll(viewModel.getAvailableGroupMemberNames());

        if (dialog.getItems().isEmpty()) {
            showAlert("Thông báo", "Không có bạn bè nào khả dụng để thêm.");
            return;
        }

        dialog.showAndWait().ifPresent(selectedUserStr -> {
            viewModel.addGroupMember(selectedUserStr);
        });
    }

    @FXML
    private void handleLeaveGroup() {
        if (!viewModel.currentChatIsGroupProperty().get() || viewModel.activeConversationProperty().get() == null) return;
        if (!dialogs.confirm(
                "Rời nhóm", null,
                "Bạn có chắc muốn rời nhóm "
                        + viewModel.currentChatNameProperty().get() + " không?")) return;
        viewModel.leaveGroup();
        conversations.resetHeader();
    }

    @FXML
    private void handleDeleteGroup() {
        String groupName = viewModel.currentChatNameProperty().get();
        if (!viewModel.currentChatIsGroupProperty().get()
                || groupName == null
                || !viewModel.isGroupCreator(groupName)) {
            showAlert("Lỗi", "Chỉ chủ nhóm mới có thể xóa nhóm.");
            return;
        }

        if (!dialogs.confirm(
                "Xóa nhóm",
                "Xóa vĩnh viễn nhóm " + groupName + "?",
                "Các thành viên sẽ không thể truy cập nhóm này nữa.")) return;
        viewModel.deleteCurrentGroup();
        chatTitleLabel.setText("Chọn cuộc trò chuyện");
        chatStatusLabel.setText("Cá nhân / Nhóm");
    }

    private void showSelectedFile(File file) {
        fileNameLabel.setText(file.getName());
        fileSizeLabel.setText(secretchat.util.FileUtils.formatFileSize(file.length()));
        filePreviewBox.setVisible(true);
        filePreviewBox.setManaged(true);
    }

    private void clearSelectedFile() {
        selectedFile = null;
        fileNameLabel.setText("");
        fileSizeLabel.setText("");
        filePreviewBox.setVisible(false);
        filePreviewBox.setManaged(false);
    }

    private void showAlert(String title, String content) {
        dialogs.showNotification(title, content);
    }
}
