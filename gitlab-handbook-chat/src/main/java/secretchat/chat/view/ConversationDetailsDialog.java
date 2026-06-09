package secretchat.chat.view;

import javafx.animation.FadeTransition;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;
import secretchat.chat.service.ConversationDetailsService;
import secretchat.chat.viewmodel.ChatViewModel;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

public final class ConversationDetailsDialog {
    private final ChatViewModel viewModel;
    private final ConversationDetailsService service = new ConversationDetailsService();
    private final Stage stage = new Stage(StageStyle.TRANSPARENT);
    private final VBox root = new VBox(14);
    private final TextField search = new TextField();
    private final ListView<Object> list = new ListView<>();

    private ConversationDetailsDialog(ChatViewModel viewModel, Window owner, String title) {
        this.viewModel = viewModel;
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        Label heading = new Label(title);
        heading.getStyleClass().add("details-dialog-title");
        search.setPromptText("Tìm kiếm...");
        search.getStyleClass().add("details-search");
        list.getStyleClass().add("details-list");
        VBox.setVgrow(list, Priority.ALWAYS);
        Button close = new Button("Đóng");
        close.getStyleClass().add("details-secondary-button");
        close.setOnAction(event -> stage.close());
        HBox footer = new HBox(close);
        footer.setAlignment(Pos.CENTER_RIGHT);
        root.getChildren().addAll(heading, search, list, footer);
        root.setPadding(new Insets(22));
        root.getStyleClass().add("details-dialog");
        Scene scene = new Scene(root, 640, 520);
        scene.setFill(null);
        scene.getStylesheets().add(getClass().getResource("/css/conversation-details.css").toExternalForm());
        stage.setScene(scene);
    }

    public static void showMembers(ChatViewModel vm, Window owner,
            java.util.function.Consumer<String> openPrivateChat) {
        ConversationDetailsDialog dialog = new ConversationDetailsDialog(vm, owner, "Thành viên nhóm");
        dialog.configureMembers(openPrivateChat);
        dialog.show();
    }

    public static void showFiles(ChatViewModel vm, Window owner) {
        ConversationDetailsDialog dialog = new ConversationDetailsDialog(vm, owner, "File đã gửi");
        dialog.configureFiles();
        dialog.show();
    }

    public static void showLinks(ChatViewModel vm, Window owner) {
        ConversationDetailsDialog dialog = new ConversationDetailsDialog(vm, owner, "Link đã gửi");
        dialog.configureLinks();
        dialog.show();
    }

    private void configureMembers(java.util.function.Consumer<String> openPrivateChat) {
        List<ChatViewModel.GroupMemberView> members = viewModel.getCurrentGroupMembers();
        setFilteredItems(members, member -> {
            ChatViewModel.GroupMemberView value = (ChatViewModel.GroupMemberView) member;
            return value.displayName() + " " + roleText(value.role());
        });
        boolean owner = viewModel.isGroupCreator(viewModel.currentChatNameProperty().get());
        list.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                ChatViewModel.GroupMemberView member = (ChatViewModel.GroupMemberView) item;
                Label avatar = new Label(member.displayName().substring(0, 1).toUpperCase());
                avatar.getStyleClass().add("details-avatar");
                VBox text = new VBox(new Label(member.displayName()), new Label(roleText(member.role())));
                text.getStyleClass().add("details-item-text");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox row = new HBox(12, avatar, text, spacer);
                row.setAlignment(Pos.CENTER_LEFT);
                if (!"Bạn".equals(member.displayName())) {
                    row.setOnMouseClicked(event -> {
                        if (event.getClickCount() == 2) {
                            openPrivateChat.accept(member.displayName());
                            stage.close();
                        }
                    });
                }
                if (owner && !"OWNER".equals(member.role())) {
                    Button transfer = actionButton("Chuyển quyền");
                    transfer.setOnAction(e -> { viewModel.transferGroupOwnership(member.userId()); stage.close(); });
                    Button remove = actionButton("Xóa");
                    remove.getStyleClass().add("details-danger-button");
                    remove.setOnAction(e -> { viewModel.removeGroupMemberById(member.userId()); stage.close(); });
                    row.getChildren().addAll(transfer, remove);
                }
                setGraphic(row);
            }
        });
        Button add = actionButton("Thêm thành viên");
        add.setOnAction(event -> addMember());
        Button leave = actionButton("Rời nhóm");
        leave.getStyleClass().add("details-danger-button");
        leave.setDisable(owner);
        if (owner) leave.setTooltip(new Tooltip("Hãy chuyển quyền chủ nhóm trước khi rời nhóm."));
        leave.setOnAction(event -> { viewModel.leaveGroup(); stage.close(); });
        HBox footer = (HBox) root.getChildren().get(root.getChildren().size() - 1);
        footer.getChildren().add(0, add);
        footer.getChildren().add(1, leave);
    }

    private void addMember() {
        List<String> candidates = viewModel.getAvailableGroupMemberNames();
        Stage addStage = new Stage(StageStyle.TRANSPARENT);
        addStage.initOwner(stage);
        addStage.initModality(Modality.WINDOW_MODAL);

        Label title = new Label("Thêm thành viên");
        title.getStyleClass().add("add-member-title");
        Label caption = new Label("Tìm và chọn một người bạn để thêm vào nhóm");
        caption.getStyleClass().add("add-member-caption");
        VBox header = new VBox(3, title, caption);
        header.getStyleClass().add("add-member-header");

        TextField memberSearch = new TextField();
        memberSearch.setPromptText("Tìm theo tên...");
        memberSearch.getStyleClass().add("add-member-search");

        FilteredList<String> filtered = new FilteredList<>(
                FXCollections.observableArrayList(candidates), ignored -> true);
        memberSearch.textProperty().addListener((obs, oldText, text) -> {
            String keyword = text == null ? "" : text.trim().toLowerCase();
            filtered.setPredicate(name -> name.toLowerCase().contains(keyword));
        });

        ListView<String> memberList = new ListView<>(filtered);
        memberList.getStyleClass().add("add-member-list");
        memberList.setPlaceholder(emptyMemberLabel());
        VBox.setVgrow(memberList, Priority.ALWAYS);

        VBox content = new VBox(10, memberSearch, memberList);
        content.getStyleClass().add("add-member-content");

        Button cancel = new Button("Hủy");
        cancel.getStyleClass().add("add-member-cancel");
        cancel.setOnAction(event -> addStage.close());

        Button add = new Button("Thêm");
        add.getStyleClass().add("add-member-button");
        add.setDefaultButton(true);
        add.disableProperty().bind(memberList.getSelectionModel().selectedItemProperty().isNull());
        add.setOnAction(event -> {
            String selected = memberList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                viewModel.addGroupMember(selected);
                addStage.close();
                stage.close();
            }
        });
        memberList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !add.isDisabled()) add.fire();
        });

        HBox footer = new HBox(10, cancel, add);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.getStyleClass().add("add-member-footer");

        VBox addRoot = new VBox(header, content, footer);
        addRoot.getStyleClass().add("add-member-dialog");
        Scene addScene = new Scene(addRoot, 480, 470);
        addScene.setFill(null);
        addScene.getStylesheets().add(
                getClass().getResource("/css/add-member-dialog.css").toExternalForm());
        addStage.setScene(addScene);
        addStage.showAndWait();
    }

    private Label emptyMemberLabel() {
        Label empty = new Label("Không có thành viên phù hợp");
        empty.getStyleClass().add("add-member-empty");
        return empty;
    }

    private void configureFiles() {
        List<ConversationDetailsService.SharedFile> files = service.files(List.copyOf(viewModel.getMessages()));
        setFilteredItems(files, file -> ((ConversationDetailsService.SharedFile) file).name());
        list.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                ConversationDetailsService.SharedFile file = (ConversationDetailsService.SharedFile) item;
                VBox info = new VBox(new Label(file.name()),
                        new Label(file.sender() + " • " + safe(file.time()) + " • " + service.formatSize(file.size())));
                Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
                Button open = actionButton("Mở/Preview");
                open.setOnAction(e -> saveFile(file, true));
                Button download = actionButton("Tải");
                download.setOnAction(e -> saveFile(file, false));
                HBox row = new HBox(10, info, spacer, open, download);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });
    }

    private void saveFile(ConversationDetailsService.SharedFile file, boolean open) {
        try {
            byte[] bytes = viewModel.downloadFile(file.message());
            File target;
            if (open) {
                target = File.createTempFile("secretchat-", "-" + file.name());
                target.deleteOnExit();
            } else {
                FileChooser chooser = new FileChooser();
                chooser.setInitialFileName(file.name());
                target = chooser.showSaveDialog(stage);
                if (target == null) return;
            }
            Files.write(target.toPath(), bytes);
            if (open && Desktop.isDesktopSupported()) Desktop.getDesktop().open(target);
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Không thể xử lý file: " + ex.getMessage()).showAndWait();
        }
    }

    private void configureLinks() {
        List<ConversationDetailsService.SharedLink> links = service.links(List.copyOf(viewModel.getMessages()));
        setFilteredItems(links, link -> ((ConversationDetailsService.SharedLink) link).url());
        list.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                ConversationDetailsService.SharedLink link = (ConversationDetailsService.SharedLink) item;
                VBox info = new VBox(new Label(link.url()), new Label(link.sender() + " • " + safe(link.time())));
                Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
                Button open = actionButton("Mở link");
                open.setOnAction(e -> {
                    try { Desktop.getDesktop().browse(java.net.URI.create(link.url())); }
                    catch (Exception ex) { new Alert(Alert.AlertType.ERROR, "Không thể mở link.").showAndWait(); }
                });
                HBox row = new HBox(10, info, spacer, open);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });
    }

    private void setFilteredItems(List<?> values, java.util.function.Function<Object, String> searchable) {
        FilteredList<Object> filtered = new FilteredList<>(FXCollections.observableArrayList(values), ignored -> true);
        search.textProperty().addListener((obs, oldText, text) -> filtered.setPredicate(item ->
                searchable.apply(item).toLowerCase().contains(text == null ? "" : text.toLowerCase())));
        list.setItems(filtered);
    }

    private Button actionButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("details-action-button");
        return button;
    }

    private String roleText(String role) {
        return "OWNER".equals(role) ? "Chủ nhóm" : "Thành viên";
    }

    private String safe(String value) { return value == null ? "" : value.replace('T', ' '); }

    private void show() {
        stage.show();
        FadeTransition fade = new FadeTransition(Duration.millis(180), root);
        fade.setFromValue(0); fade.setToValue(1); fade.play();
    }
}
