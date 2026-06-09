package secretchat.chat.controller;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import secretchat.chat.viewmodel.ChatViewModel;
import secretchat.chat.viewmodel.MainViewModel;
import secretchat.chat.view.ConversationDetailsDialog;
import secretchat.chat.service.DesktopNotificationService;
import secretchat.dto.response.GroupResponse;

import java.io.File;
import java.io.IOException;

public class ChatController extends BaseChatController {

    @FXML private TextField searchField;
    @FXML private ListView<String> privateChatList;
    @FXML private ListView<String> groupChatList;
    @FXML private TabPane chatTabPane;
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
    @FXML private Button scrollBottomButton;
    @FXML private Label searchResultLabel;
    @FXML private Button aiAssistantButton;

    private static final System.Logger LOGGER = System.getLogger(ChatController.class.getName());
    private ChatViewModel viewModel;
    private File selectedFile;
    private final PauseTransition typingPause = new PauseTransition(Duration.millis(900));
    private final PauseTransition pinnedCollapsePause = new PauseTransition(Duration.millis(120));
    private final Timeline typingAnimation = new Timeline();
    private String typingBaseText = "Đang nhập";
    private final DesktopNotificationService desktopNotifications = new DesktopNotificationService();
    private Popup activeToast;
    private boolean pinnedCollapsed = true;
    private boolean pinnedExpandedByUser;
    private boolean pinnedContextMenuOpen;

    @FXML
    public void initialize() {
        viewModel = new ChatViewModel();
        
        // Setup bindings
        privateChatList.setItems(viewModel.getPrivateChatList());
        groupChatList.setItems(viewModel.getGroupChatList());
        pinnedMessageList.setItems(viewModel.getPinnedMessageList());
        updatePinnedPanel();
        viewModel.getPinnedMessageList().addListener(
                (ListChangeListener<ChatViewModel.PinnedMessageItem>) change -> updatePinnedPanel());
        typingLabel.textProperty().bind(viewModel.typingTextProperty());
        typingLabel.visibleProperty().bind(viewModel.typingTextProperty().isNotNull());
        typingLabel.managedProperty().bind(typingLabel.visibleProperty());
        aiProgressIndicator.visibleProperty().bind(viewModel.aiLoadingProperty());
        aiProgressIndicator.managedProperty().bind(aiProgressIndicator.visibleProperty());
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
                            renderMessageItem(item);
                        }
                    }
                }
            }
            if (rebuild) {
                renderAllMessages();
            }
            if (added) {
                scrollToBottomAfterLayout();
            }
        });

        viewModel.currentChatIsGroupProperty().addListener((obs, oldVal, newVal) -> {
            updateRightPanel(newVal);
        });
        viewModel.activeConversationProperty().addListener((obs, oldVal, newVal) ->
        {
            if (newVal != null && newVal != oldVal) {
                pinnedCollapsed = true;
                pinnedExpandedByUser = false;
            }
            updateRightPanel(viewModel.currentChatIsGroupProperty().get());
            updatePinnedPanel();
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
        setupPinnedMessageList();
        pinnedCollapsePause.setOnFinished(event -> collapsePinnedWhenPointerLeaves());
        pinnedArea.hoverProperty().addListener((obs, wasHovered, isHovered) -> {
            if (isHovered) {
                pinnedCollapsePause.stop();
            } else if (!pinnedContextMenuOpen) {
                pinnedCollapsePause.playFromStart();
            }
        });
        pinnedContent.hoverProperty().addListener((obs, wasHovered, isHovered) -> {
            if (isHovered) {
                pinnedCollapsePause.stop();
            } else if (!pinnedContextMenuOpen) {
                pinnedCollapsePause.playFromStart();
            }
        });

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
                clearConversationUI();
                if (isGroupTab) {
                    groupChatList.getSelectionModel().clearSelection();
                } else {
                    privateChatList.getSelectionModel().clearSelection();
                }
            }
            updateRightPanel(isGroupTab);
        });

        updateRightPanel(false);
        viewModel.init();

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
                    desktopNotifications.close();
                });
            }
        });
    }

    private void setupCellFactories() {
        javafx.util.Callback<ListView<String>, ListCell<String>> chatListCellFactory = param -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("ai-list-cell");
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox box = new HBox();
                    box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    Label nameLabel = new Label(item);
                    
                    boolean isAi = "TRỢ LÝ AI".equals(item);
                    if (isAi) {
                        getStyleClass().add("ai-list-cell");
                        FontIcon aiIcon = new FontIcon("fa-magic");
                        aiIcon.setIconSize(13);
                        aiIcon.setIconColor(javafx.scene.paint.Color.web("#8b5cf6"));
                        box.getChildren().add(aiIcon);
                        HBox.setMargin(aiIcon, new javafx.geometry.Insets(0, 8, 0, 0));
                    }
                    
                    int unread = 0;
                    if (param == privateChatList) {
                        String uid = viewModel.getUserIdByDisplayName(item);
                        if (uid != null) unread = viewModel.getUnreadCountForUser(uid);
                    } else if (param == groupChatList) {
                        GroupResponse g = viewModel.getGroupByName(item);
                        if (g != null) unread = viewModel.getUnreadCountForGroup(g.getId());
                    }

                    box.getChildren().add(nameLabel);
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    box.getChildren().add(spacer);

                    if (unread > 0) {
                        Label badge = new Label(String.valueOf(unread));
                        badge.getStyleClass().add("conversation-unread-badge");
                        HBox.setMargin(badge, new javafx.geometry.Insets(0, 0, 0, 10));
                        box.getChildren().add(badge);
                    }
                    
                    if (isAi) {
                        Label aiBadge = new Label("AI");
                        aiBadge.setStyle("-fx-background-color: linear-gradient(to right, #8b5cf6, #ec4899); -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 1 5; -fx-font-size: 9px; -fx-font-weight: bold;");
                        HBox.setMargin(aiBadge, new javafx.geometry.Insets(0, 0, 0, 6));
                        box.getChildren().add(aiBadge);
                    }

                    if (param == privateChatList && !isAi) {
                        Button moreButton = new Button();
                        FontIcon moreIcon = new FontIcon("fa-ellipsis-v");
                        moreIcon.setIconSize(14);
                        moreButton.setGraphic(moreIcon);
                        moreButton.getStyleClass().add("conversation-more-button");
                        ContextMenu menu = new ContextMenu();
                        MenuItem removeFriend = new MenuItem("Xóa bạn bè");
                        removeFriend.setOnAction(event -> confirmRemoveFriend(item));
                        menu.getItems().add(removeFriend);
                        moreButton.setOnAction(event -> {
                            menu.show(moreButton, javafx.geometry.Side.BOTTOM, 0, 0);
                            event.consume();
                        });
                        HBox.setMargin(moreButton, new javafx.geometry.Insets(0, 0, 0, 8));
                        box.getChildren().add(moreButton);
                    }
                    
                    setGraphic(box);
                }
            }
        };

        privateChatList.setCellFactory(chatListCellFactory);
        groupChatList.setCellFactory(chatListCellFactory);

    }

    private void setupPinnedMessageList() {
        pinnedMessageList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(ChatViewModel.PinnedMessageItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                StackPane typeIcon = createPinnedTypeIcon(item.type());
                Label content = new Label(item.preview());
                content.setMaxWidth(520);
                content.setTextOverrun(OverrunStyle.ELLIPSIS);
                content.getStyleClass().add("pinned-message-content");

                String metaText = item.sender();
                if (item.time() != null && !item.time().isBlank()) metaText += " • " + item.time();
                Label meta = new Label(metaText);
                meta.getStyleClass().add("pinned-message-meta");

                VBox text = new VBox(2, content, meta);
                HBox.setHgrow(text, Priority.ALWAYS);
                Button more = new Button();
                more.setGraphic(new FontIcon("fa-ellipsis-h"));
                more.getStyleClass().add("pinned-more-button");
                ContextMenu menu = new ContextMenu();
                MenuItem goTo = new MenuItem("Đi tới tin nhắn gốc");
                goTo.setOnAction(event -> {
                    goToPinnedMessage(item);
                    collapsePinnedPanel();
                });
                MenuItem unpin = new MenuItem("Bỏ ghim");
                unpin.setOnAction(event -> {
                    viewModel.unpinMessage(item);
                    collapsePinnedPanel();
                });
                menu.getItems().addAll(goTo, unpin);
                menu.setOnShowing(event -> {
                    pinnedContextMenuOpen = true;
                    pinnedCollapsePause.stop();
                });
                menu.setOnHidden(event -> {
                    pinnedContextMenuOpen = false;
                    if (!pinnedArea.isHover() && !pinnedContent.isHover()) {
                        pinnedCollapsePause.playFromStart();
                    }
                });
                more.setOnMouseClicked(event -> event.consume());
                more.setOnAction(event -> {
                    menu.show(more, javafx.geometry.Side.BOTTOM, 0, 0);
                    event.consume();
                });
                HBox row = new HBox(10, typeIcon, text, more);
                row.getStyleClass().add("pinned-message-row");
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                row.setOnMouseClicked(event -> {
                    if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY
                            && event.getClickCount() == 1) {
                        goToPinnedMessage(item);
                    }
                });
                setGraphic(row);
            }
        });
    }

    private StackPane createPinnedTypeIcon(String type) {
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

    private void goToPinnedMessage(ChatViewModel.PinnedMessageItem pinned) {
        viewModel.ensureMessageLoaded(pinned.messageId())
                .thenAccept(item -> {
                    if (item != null) Platform.runLater(() -> scrollToMessage(item));
                });
    }

    private void collapsePinnedWhenPointerLeaves() {
        if (!pinnedArea.isHover() && !pinnedContent.isHover()
                && !pinnedContextMenuOpen
                && pinnedExpandedByUser && !pinnedCollapsed) {
            pinnedCollapsed = true;
            pinnedExpandedByUser = false;
            updatePinnedPanel();
        }
    }

    private void updatePinnedPanel() {
        boolean hasConversation = viewModel != null
                && viewModel.activeConversationProperty().get() != null;
        int count = viewModel == null ? 0 : viewModel.getPinnedMessageList().size();
        boolean showPinnedArea = hasConversation && count > 0;
        pinnedArea.setVisible(showPinnedArea);
        pinnedArea.setManaged(showPinnedArea);
        pinnedTitleLabel.setText("Danh sách ghim (" + count + ")");
        pinnedContent.setVisible(showPinnedArea && !pinnedCollapsed);
        pinnedContent.setManaged(showPinnedArea && !pinnedCollapsed);
        pinnedMessageList.setVisible(count > 0);
        pinnedMessageList.setManaged(count > 0);
        pinnedEmptyLabel.setVisible(count == 0);
        pinnedEmptyLabel.setManaged(count == 0);
        pinnedCollapseButton.setText(pinnedCollapsed ? "Mở rộng" : "Thu gọn");
        if (showPinnedArea && !pinnedCollapsed) {
            Platform.runLater(() -> {
                pinnedMessageList.refresh();
                pinnedContent.applyCss();
                pinnedContent.layout();
                pinnedArea.requestLayout();
            });
        }
    }

    @FXML
    private void handleTogglePinned() {
        pinnedCollapsed = !pinnedCollapsed;
        pinnedExpandedByUser = !pinnedCollapsed;
        updatePinnedPanel();
    }

    private void collapsePinnedPanel() {
        if (!pinnedCollapsed) {
            pinnedCollapsed = true;
            pinnedExpandedByUser = false;
            updatePinnedPanel();
        }
    }

    private void renderAllMessages() {
        messageContainer.getChildren().clear();
        for (ChatViewModel.MessageItem item : viewModel.getMessages()) {
            renderMessageItem(item);
        }
    }

    private void handleNewMessageNotification(ChatViewModel.NewMessageEvent event) {
        Stage stage = (Stage) messageInput.getScene().getWindow();
        boolean viewingConversation = viewModel.activeConversationProperty().get() != null
                && event.conversationId().equals(viewModel.activeConversationProperty().get().getId());
        if (stage.isFocused() && viewingConversation) return;

        Runnable openConversation = () -> {
            stage.setIconified(false);
            stage.show();
            stage.toFront();
            stage.requestFocus();
            openConversation(event);
        };

        if (stage.isIconified() || !stage.isShowing()) {
            desktopNotifications.show(event.chatName(), event.preview(), openConversation);
        } else {
            showMessageToast(event, openConversation);
        }
    }

    private void showMessageToast(ChatViewModel.NewMessageEvent event, Runnable onClick) {
        if (activeToast != null) activeToast.hide();

        Label title = new Label(event.chatName());
        title.getStyleClass().add("message-toast-title");
        Label preview = new Label(event.preview());
        preview.setWrapText(true);
        preview.setMaxWidth(300);
        preview.getStyleClass().add("message-toast-preview");
        VBox content = new VBox(4, title, preview);
        content.getStyleClass().add("message-toast");
        content.getStylesheets().add(
                getClass().getResource("/css/chat.css").toExternalForm());
        content.setOnMouseClicked(mouseEvent -> {
            activeToast.hide();
            onClick.run();
        });

        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.getContent().add(content);
        activeToast = popup;

        Stage stage = (Stage) messageInput.getScene().getWindow();
        content.applyCss();
        popup.show(stage);
        Platform.runLater(() -> {
            popup.setX(stage.getX() + stage.getWidth() - content.prefWidth(-1) - 24);
            popup.setY(stage.getY() + 72);
        });
        PauseTransition hide = new PauseTransition(Duration.seconds(5));
        hide.setOnFinished(event1 -> popup.hide());
        hide.play();
    }

    private void openConversation(ChatViewModel.NewMessageEvent event) {
        if (event.group()) {
            chatTabPane.getSelectionModel().select(1);
            groupChatList.getSelectionModel().select(event.chatName());
            chatTitleLabel.setText(event.chatName());
            GroupResponse group = viewModel.getGroupByName(event.chatName());
            chatStatusLabel.setText(group != null && group.getDescription() != null
                    ? "Chat nhóm - " + group.getDescription() : "Chat nhóm");
            viewModel.selectGroupChat(event.chatName());
            groupChatList.refresh();
        } else {
            chatTabPane.getSelectionModel().select(0);
            privateChatList.getSelectionModel().select(event.chatName());
            chatTitleLabel.setText(event.chatName());
            chatStatusLabel.setText("Chat cá nhân");
            viewModel.selectPrivateChat(event.chatName());
            privateChatList.refresh();
        }
    }

    private void confirmRemoveFriend(String displayName) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        styleAlert(confirm);
        confirm.setTitle("Xóa bạn bè");
        confirm.setHeaderText("Xóa " + displayName + " khỏi danh sách bạn bè?");
        confirm.setContentText("Lịch sử tin nhắn sẽ được giữ lại.");
        confirm.showAndWait().ifPresent(button -> {
            if (button == ButtonType.OK) {
                boolean wasCurrentChat = displayName.equals(viewModel.currentChatNameProperty().get());
                viewModel.removeFriend(displayName);
                if (wasCurrentChat) {
                    chatTitleLabel.setText("Chọn cuộc trò chuyện");
                    chatStatusLabel.setText("Cá nhân / Nhóm");
                }
            }
        });
    }

    private String extractMemberName(String displayedText) {
        if (displayedText == null) return "";
        if (displayedText.endsWith(" (Chủ nhóm)")) {
            return displayedText.substring(0, displayedText.length() - 13);
        } else if (displayedText.endsWith(" (Phó nhóm)")) {
            return displayedText.substring(0, displayedText.length() - 13);
        }
        return displayedText;
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
        chatTabPane.getSelectionModel().select(0);
        chatTitleLabel.setText(memberName);
        chatStatusLabel.setText("Chat cá nhân");
        viewModel.openPrivateChatForMember(memberName);
        privateChatList.getSelectionModel().select(memberName);
    }

    @FXML
    private void handleSelectPrivateChat() {
        String selectedUserStr = privateChatList.getSelectionModel().getSelectedItem();
        if (selectedUserStr == null) return;

        chatTitleLabel.setText(selectedUserStr);
        chatStatusLabel.setText("Chat cá nhân");
        
        viewModel.selectPrivateChat(selectedUserStr);
        privateChatList.refresh();
    }

    @FXML
    private void handleSelectAIAssistant() {
        String aiAssistantName = "TRỢ LÝ AI";
        chatTitleLabel.setText(aiAssistantName);
        chatStatusLabel.setText("Trợ lý ảo thông minh");
        
        viewModel.selectPrivateChat(aiAssistantName);
        privateChatList.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleSelectGroupChat() {
        String selectedGroupStr = groupChatList.getSelectionModel().getSelectedItem();
        if (selectedGroupStr == null) return;

        chatTitleLabel.setText(selectedGroupStr);
        GroupResponse g = viewModel.getGroupByName(selectedGroupStr);
        if (g != null && g.getDescription() != null) {
            chatStatusLabel.setText("Chat nhóm - " + g.getDescription());
        } else {
            chatStatusLabel.setText("Chat nhóm");
        }

        viewModel.selectGroupChat(selectedGroupStr);
        groupChatList.refresh();
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
            scrollToMessage(result);
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
        scrollToBottom();
    }

    @FXML
    private void handleAttachFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn file");
        selectedFile = fileChooser.showOpenDialog(messageInput.getScene().getWindow());
        validateAndShowSelectedFile();
    }

    @FXML
    private void handleAttachImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );
        selectedFile = fileChooser.showOpenDialog(messageInput.getScene().getWindow());
        validateAndShowSelectedFile();
    }

    private void validateAndShowSelectedFile() {
        if (selectedFile == null) return;
        if (selectedFile.length() > 104857600L) {
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
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/create-group-dialog.fxml"));
            Parent root = loader.load();

            CreateGroupController controller = loader.getController();
            controller.setOnGroupCreated((groupName, groupDesc) -> {
                viewModel.createGroup(groupName, groupDesc);
            });

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initStyle(StageStyle.UNDECORATED);
            dialog.setScene(new Scene(root));
            dialog.setResizable(false);
            dialog.showAndWait();
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Lỗi khi mở dialog tạo nhóm", e);
        }
    }

    @FXML
    private void handleAddFriend() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/add-friend-dialog.fxml"));
            Parent root = loader.load();

            AddFriendController controller = loader.getController();
            controller.setOnFriendAdded(username -> {
                viewModel.addFriend(username);
            });

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initStyle(StageStyle.UNDECORATED);
            dialog.setScene(new Scene(root));
            dialog.setResizable(false);
            dialog.showAndWait();
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Lỗi khi mở dialog thêm bạn", e);
        }
    }

    private void openPrivateChatFromMemberProfile(secretchat.dto.response.UserResponse profile) {
        if (profile == null) return;
        String displayName = profile.getUsername() == null || profile.getUsername().isBlank()
                ? profile.getFullName() : profile.getUsername();
        chatTabPane.getSelectionModel().select(0);
        chatTitleLabel.setText(displayName);
        chatStatusLabel.setText("Chat cá nhân");
        viewModel.openPrivateChatForProfile(profile);
        privateChatList.getSelectionModel().select(displayName);
        privateChatList.refresh();
    }

    @FXML
    private void handleProfile() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/profile-dialog.fxml"));
            Parent root = loader.load();
            ProfileDialogController controller = loader.getController();
            controller.setViewModel(viewModel);

            Stage dialog = new Stage();
            dialog.initOwner(messageInput.getScene().getWindow());
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initStyle(StageStyle.UNDECORATED);
            dialog.setScene(new Scene(root));
            dialog.setResizable(false);
            dialog.showAndWait();
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Lỗi khi mở dialog trang cá nhân", e);
            showAlert("Lỗi", "Không thể mở trang cá nhân.");
        }
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
        desktopNotifications.close();
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

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        styleAlert(confirm);
        confirm.setTitle("Rời nhóm");
        confirm.setHeaderText(null);
        confirm.setContentText("Bạn có chắc muốn rời nhóm " + viewModel.currentChatNameProperty().get() + " không?");

        confirm.showAndWait().ifPresent(buttonType -> {
            if (buttonType == ButtonType.OK) {
                viewModel.leaveGroup();
                chatTitleLabel.setText("Chọn cuộc trò chuyện");
                chatStatusLabel.setText("Cá nhân / Nhóm");
            }
        });
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

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        styleAlert(confirm);
        confirm.setTitle("Xóa nhóm");
        confirm.setHeaderText("Xóa vĩnh viễn nhóm " + groupName + "?");
        confirm.setContentText("Các thành viên sẽ không thể truy cập nhóm này nữa.");
        confirm.showAndWait().ifPresent(buttonType -> {
            if (buttonType == ButtonType.OK) {
                viewModel.deleteCurrentGroup();
                chatTitleLabel.setText("Chọn cuộc trò chuyện");
                chatStatusLabel.setText("Cá nhân / Nhóm");
            }
        });
    }

    private void renderMessageItem(ChatViewModel.MessageItem item) {
        HBox wrapper = new HBox();
        wrapper.getStyleClass().addAll("message-row", item.isMe() ? "my-message-row" : "other-message-row");
        wrapper.setAlignment(item.isMe() ? javafx.geometry.Pos.CENTER_RIGHT : javafx.geometry.Pos.CENTER_LEFT);

        VBox box = new VBox(item.isMe() ? 2 : 4);
        box.setAlignment(item.isMe() ? javafx.geometry.Pos.CENTER_RIGHT : javafx.geometry.Pos.CENTER_LEFT);

        if (!item.isMe()) {
            Label senderLabel = new Label(item.getSenderName());
            senderLabel.getStyleClass().add("message-sender");
            box.getChildren().add(senderLabel);
        }

        javafx.scene.Node contentNode;

        if (item.isFile()) {
            HBox fileBox = new HBox(12);
            fileBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            if (item.isMe()) {
                fileBox.getStyleClass().add("chat-file-message");
            } else {
                fileBox.getStyleClass().add("other-file-message");
            }

            StackPane iconPane = new StackPane();
            FontIcon fileIcon = new FontIcon("fa-file");
            fileIcon.setIconSize(36);
            fileIcon.getStyleClass().add("file-message-icon");

            String ext = "FILE";
            if (item.getContent() != null) {
                int lastDot = item.getContent().lastIndexOf('.');
                if (lastDot > 0 && lastDot < item.getContent().length() - 1) {
                    ext = item.getContent().substring(lastDot + 1).toUpperCase();
                    if (ext.length() > 4) ext = ext.substring(0, 4);
                }
            }
            
            Label extLabel = new Label(ext);
            if (item.isMe()) {
                extLabel.setStyle("-fx-font-size: 9px; -fx-font-weight: 900; -fx-text-fill: #6366f1; -fx-background-color: white; -fx-padding: 1 4; -fx-background-radius: 4;");
            } else {
                extLabel.setStyle("-fx-font-size: 9px; -fx-font-weight: 900; -fx-text-fill: white; -fx-background-color: #6366f1; -fx-padding: 1 4; -fx-background-radius: 4;");
            }
            StackPane.setAlignment(extLabel, javafx.geometry.Pos.CENTER);
            StackPane.setMargin(extLabel, new javafx.geometry.Insets(8, 0, 0, 0));
            
            iconPane.getChildren().addAll(fileIcon, extLabel);

            VBox fileDetails = new VBox(3);
            Label name = new Label(item.getContent());
            name.setWrapText(true);
            name.getStyleClass().add("file-message-name");

            Label size = new Label();
            if (item.getResponse() != null && item.getResponse().getFileSize() != null) {
                size.setText(secretchat.util.FileUtils.formatFileSize(item.getResponse().getFileSize()));
            }
            size.getStyleClass().add("file-message-size");

            fileDetails.getChildren().addAll(name, size);
            Region fileSpacer = new Region();
            HBox.setHgrow(fileSpacer, Priority.ALWAYS);
            StackPane progressPane = createUploadProgress(item);
            fileBox.getChildren().addAll(iconPane, fileDetails, fileSpacer, progressPane);

            contentNode = fileBox;
            attachFileClickHandler(fileBox, item);
        } else {
            Label bubble = new Label(item.getContent());
            bubble.setWrapText(true);
            bubble.setMaxWidth(350);
            if (!item.isMe() && "TRỢ LÝ AI".equals(item.getSenderName())) {
                bubble.getStyleClass().addAll("chat-message-bubble", "ai-message");
            } else {
                bubble.getStyleClass().addAll("chat-message-bubble", item.isMe() ? "my-message" : "other-message");
            }
            contentNode = bubble;
            item.contentProperty().addListener((obs, oldText, newText) -> bubble.setText(newText));
        }

        // Apply delete/recall styling initially
        if (item.isDeleted() || item.isDeletedForMe()) {
            if (contentNode instanceof Label bubble) {
                bubble.setText(item.isDeleted() ? (item.isMe() ? "Bạn đã thu hồi tin nhắn này" : "Tin nhắn đã bị thu hồi") 
                                                : (item.isMe() ? "Bạn đã xóa tin nhắn này" : "Bạn đã xóa tin nhắn này"));
                bubble.setStyle("-fx-font-style: italic; -fx-text-fill: " + (item.isMe() ? "white" : "gray") + ";");
            } else if (contentNode instanceof VBox) {
                Label fallback = new Label(item.isDeleted() ? (item.isMe() ? "Bạn đã thu hồi file này" : "File đã bị thu hồi") 
                                                            : (item.isMe() ? "Bạn đã xóa file này" : "Bạn đã xóa file này"));
                fallback.getStyleClass().addAll("chat-message-bubble", item.isMe() ? "my-message" : "other-message");
                fallback.setStyle("-fx-font-style: italic; -fx-text-fill: " + (item.isMe() ? "white" : "gray") + ";");
                contentNode = fallback;
            }
        } else {
            ContextMenu messageMenu = attachContextMenu(contentNode, item, wrapper);
            Button moreButton = new Button("⋮");
            moreButton.getStyleClass().add("message-more-button");
            moreButton.setOnAction(event -> messageMenu.show(
                    moreButton, javafx.geometry.Side.BOTTOM, 0, 0));
            box.getChildren().add(moreButton);
        }

        // Listen for future deletes/recalls
        item.isDeletedProperty().addListener((obs, oldV, newV) -> {
            if (newV) {
                Platform.runLater(() -> {
                    Label l = new Label("Bạn đã thu hồi tin nhắn này");
                    l.getStyleClass().addAll("chat-message-bubble", "my-message");
                    l.setStyle("-fx-font-style: italic; -fx-text-fill: white;");
                    
                    int idx = item.isMe() ? 0 : 1;
                    box.getChildren().set(idx, l);
                });
            }
        });
        
        item.isDeletedForMeProperty().addListener((obs, oldV, newV) -> {
            if (newV) {
                Platform.runLater(() -> {
                    Label l = new Label("Bạn đã xóa tin nhắn này");
                    l.getStyleClass().addAll("chat-message-bubble", item.isMe() ? "my-message" : "other-message");
                    l.setStyle("-fx-font-style: italic; -fx-text-fill: " + (item.isMe() ? "white" : "gray") + ";");
                    
                    int idx = item.isMe() ? 0 : 1;
                    box.getChildren().set(idx, l);
                });
            }
        });

        box.getChildren().add(contentNode);
        
        Label timeLabel = new Label(item.getTime());
        timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #9ca3af;");
        box.getChildren().add(timeLabel);

        if (item.isMe()) {
            Label statusLabel = new Label();
            statusLabel.getStyleClass().add("message-status");
            Runnable refreshStatus = () -> {
                String text = switch (item.getStatus()) {
                    case "SENDING" -> "Đang gửi";
                    case "FAILED" -> "Gửi lỗi";
                    case "SEEN" -> "Đã xem";
                    case "DELIVERED" -> "Đã gửi";
                    default -> "Đã gửi";
                };
                statusLabel.getStyleClass().removeAll(
                        "message-status-sending", "message-status-sent",
                        "message-status-seen", "message-status-failed");
                statusLabel.getStyleClass().add(switch (item.getStatus()) {
                    case "SENDING" -> "message-status-sending";
                    case "FAILED" -> "message-status-failed";
                    case "SEEN" -> "message-status-seen";
                    default -> "message-status-sent";
                });
                if (item.isStarred()) text += "  ★";
                if (item.isPinned()) text += "  📌";
                statusLabel.setText(text);
            };
            item.statusProperty().addListener((obs, oldValue, newValue) -> refreshStatus.run());
            item.starredProperty().addListener((obs, oldValue, newValue) -> refreshStatus.run());
            item.pinnedProperty().addListener((obs, oldValue, newValue) -> refreshStatus.run());
            refreshStatus.run();
            box.getChildren().add(statusLabel);
        }

        wrapper.getChildren().add(box);
        messageContainer.getChildren().add(wrapper);
    }

    private ContextMenu attachContextMenu(javafx.scene.Node node, ChatViewModel.MessageItem item, HBox wrapper) {
        ContextMenu contextMenu = new ContextMenu();
        
        if (node instanceof Label && !item.isFile()) {
            MenuItem copyItem = new MenuItem("Sao chép");
            copyItem.setOnAction(e -> {
                String text = ((Label) node).getText();
                if (text != null && !text.isEmpty()) {
                    javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                    javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                    content.putString(text);
                    clipboard.setContent(content);
                }
            });
            contextMenu.getItems().add(copyItem);
        }

        if (hasPersistedMessageId(item)) {
            if (item.isMe() && !item.isFile()) {
                MenuItem editItem = new MenuItem("Chỉnh sửa");
                editItem.setOnAction(e -> {
                    TextInputDialog dialog = new TextInputDialog(item.getContent());
                    dialog.setTitle("Chỉnh sửa tin nhắn");
                    dialog.setHeaderText(null);
                    dialog.setContentText("Nội dung:");
                    dialog.showAndWait().filter(text -> !text.isBlank())
                            .ifPresent(text -> viewModel.editMessage(item, text));
                });
                contextMenu.getItems().add(editItem);
            }

            MenuItem starItem = new MenuItem(item.isStarred() ? "Bỏ đánh dấu sao" : "Đánh dấu sao");
            starItem.setOnAction(e -> viewModel.toggleStar(item));
            contextMenu.getItems().add(starItem);

            MenuItem pinItem = new MenuItem(item.isPinned() ? "Bỏ ghim" : "Ghim tin nhắn");
            pinItem.setOnAction(e -> viewModel.togglePin(item));
            item.pinnedProperty().addListener((obs, oldValue, pinned) ->
                    pinItem.setText(pinned ? "Bỏ ghim" : "Ghim tin nhắn"));
            contextMenu.getItems().add(pinItem);

            MenuItem deleteItem = new MenuItem("Xóa");
            deleteItem.setOnAction(e -> {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/message-action-dialog.fxml"));
                    Parent root = loader.load();
                    MessageActionDialogController controller = loader.getController();
                    
                    Stage dialog = new Stage();
                    controller.setup("Xác nhận xóa", "Bạn có chắc chắn muốn xóa tin nhắn này không? Tin nhắn sẽ chỉ bị xóa ở phía bạn.", "Xóa", () -> {
                        viewModel.deleteMessageForUser(item.getResponse(), item);
                    });
                    
                    dialog.initModality(Modality.APPLICATION_MODAL);
                    dialog.initStyle(StageStyle.UNDECORATED);
                    dialog.setScene(new Scene(root));
                    dialog.setResizable(false);
                    dialog.showAndWait();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            contextMenu.getItems().add(deleteItem);
            
            if (item.isMe()) {
                MenuItem recallItem = new MenuItem("Thu hồi");
                recallItem.setOnAction(e -> {
                    try {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/message-action-dialog.fxml"));
                        Parent root = loader.load();
                        MessageActionDialogController controller = loader.getController();
                        
                        Stage dialog = new Stage();
                        controller.setup("Xác nhận thu hồi", "Bạn có chắc chắn muốn thu hồi tin nhắn này không? Hành động này sẽ thu hồi với tất cả mọi người.", "Thu hồi", () -> {
                            viewModel.recallMessage(item.getResponse(), item);
                        });
                        
                        dialog.initModality(Modality.APPLICATION_MODAL);
                        dialog.initStyle(StageStyle.UNDECORATED);
                        dialog.setScene(new Scene(root));
                        dialog.setResizable(false);
                        dialog.showAndWait();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
                contextMenu.getItems().add(recallItem);
            }
        }
        
        if (!contextMenu.getItems().isEmpty()) {
            node.setOnContextMenuRequested(e -> {
                contextMenu.show(node, e.getScreenX(), e.getScreenY());
            });
        }
        return contextMenu;
    }

    private StackPane createUploadProgress(ChatViewModel.MessageItem item) {
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

    private void attachFileClickHandler(javafx.scene.Node node, ChatViewModel.MessageItem item) {
        if (!hasPersistedMessageId(item)) return;
        node.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/download-dialog.fxml"));
                    Parent root = loader.load();
                    DownloadDialogController controller = loader.getController();
                    
                    Stage dialog = new Stage();
                    controller.setup(item.getResponse().getFileName(), item.getResponse().getFileSize() != null ? secretchat.util.FileUtils.formatFileSize(item.getResponse().getFileSize()) : "Không xác định", () -> {
                        try {
                            byte[] data = viewModel.downloadFile(item.getResponse());
                            FileChooser fileChooser = new FileChooser();
                            fileChooser.setInitialFileName(item.getResponse().getFileName());
                            File saveFile = fileChooser.showSaveDialog(node.getScene().getWindow());
                            if (saveFile != null) {
                                java.nio.file.Files.write(saveFile.toPath(), data);
                                showAlert("Thành công", "Đã tải file thành công: " + saveFile.getAbsolutePath());
                            }
                        } catch (Exception ex) {
                            showAlert("Lỗi", "Không thể tải file: " + ex.getMessage());
                        }
                    });

                    dialog.initModality(Modality.APPLICATION_MODAL);
                    dialog.initStyle(StageStyle.UNDECORATED);
                    dialog.setScene(new Scene(root));
                    dialog.setResizable(false);
                    dialog.showAndWait();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
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

    private void scrollToBottom() {
        scrollToBottomAfterLayout();
    }

    private void scrollToBottomAfterLayout() {
        Platform.runLater(() -> {
            messageContainer.applyCss();
            messageContainer.layout();
            messageScrollPane.applyCss();
            messageScrollPane.layout();
            Platform.runLater(() -> messageScrollPane.setVvalue(1.0));
        });
    }

    private boolean hasPersistedMessageId(ChatViewModel.MessageItem item) {
        return item.getResponse() != null
                && item.getResponse().getId() != null
                && !item.getResponse().getId().startsWith("pending-");
    }

    private void scrollToMessage(ChatViewModel.MessageItem target) {
        if (target == null) return;
        int index = viewModel.getMessages().indexOf(target);
        if (index < 0 || index >= messageContainer.getChildren().size()) return;
        javafx.scene.Node rowNode = messageContainer.getChildren().get(index);
        javafx.scene.Node highlightTarget = rowNode;
        if (rowNode instanceof HBox row && !row.getChildren().isEmpty()) {
            highlightTarget = row.getChildren().get(0);
        }
        highlightNode(highlightTarget);
        Platform.runLater(() -> {
            double contentHeight = messageContainer.getHeight();
            double nodeMinY = rowNode.getBoundsInParent().getMinY();
            double nodeMaxY = rowNode.getBoundsInParent().getMaxY();
            double viewportHeight = messageScrollPane.getViewportBounds().getHeight();
            if (contentHeight > viewportHeight) {
                double targetScroll = (nodeMinY + (nodeMaxY - nodeMinY) / 2 - viewportHeight / 2)
                        / (contentHeight - viewportHeight);
                messageScrollPane.setVvalue(Math.max(0, Math.min(1, targetScroll)));
            }
        });
    }

    private void highlightNode(javafx.scene.Node node) {
        if (!node.getStyleClass().contains("message-search-match")) {
            node.getStyleClass().add("message-search-match");
        }
        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(event -> node.getStyleClass().remove("message-search-match"));
        pause.play();
    }

    private void showAlert(String title, String content) {
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/notification-dialog.fxml"));
                Parent root = loader.load();
                NotificationDialogController controller = loader.getController();
                
                controller.setup(title, content);
                
                Stage dialog = new Stage();
                dialog.initModality(Modality.APPLICATION_MODAL);
                dialog.initStyle(StageStyle.UNDECORATED);
                dialog.setScene(new Scene(root));
                dialog.setResizable(false);
                dialog.showAndWait();
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.ERROR, "Lỗi khi hiển thị alert", e);
            }
        });
    }

    private void styleAlert(Alert alert) {
        try {
            alert.getDialogPane().getStylesheets().add(getClass().getResource("/css/chat.css").toExternalForm());
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Không thể tải CSS cho dialog: {0}", e.getMessage());
        }
    }

    private void openWebLink(String url) {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (!url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://")) {
                url = "https://" + url;
            }
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            } else {
                Runtime runtime = Runtime.getRuntime();
                if (os.contains("win")) {
                    runtime.exec("rundll32 url.dll,FileProtocolHandler " + url);
                } else if (os.contains("mac")) {
                    runtime.exec("open " + url);
                } else if (os.contains("nix") || os.contains("nux")) {
                    runtime.exec("xdg-open " + url);
                } else {
                    LOGGER.log(System.Logger.Level.WARNING, "Môi trường không hỗ trợ mở link: {0}", url);
                }
            }
        } catch (Exception ex) {
            LOGGER.log(System.Logger.Level.ERROR, "Lỗi khi mở link: " + url, ex);
        }
    }
}
