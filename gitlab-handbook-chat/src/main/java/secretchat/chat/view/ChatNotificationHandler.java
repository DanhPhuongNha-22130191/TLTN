package secretchat.chat.view;

import javafx.beans.value.ChangeListener;
import javafx.stage.Stage;
import secretchat.chat.viewmodel.ChatViewModel;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class ChatNotificationHandler implements AutoCloseable {

    private final ChatViewModel viewModel;
    private final ConversationNavigator conversations;
    private final NewMessageNotifier notifier;
    private final Supplier<Stage> stageSupplier;
    private final BiConsumer<String, String> alertHandler;
    private final ChangeListener<String> errorListener;
    private final ChangeListener<String> notificationListener;
    private final ChangeListener<ChatViewModel.NewMessageEvent> messageListener;

    public ChatNotificationHandler(
            ChatViewModel viewModel,
            ConversationNavigator conversations,
            NewMessageNotifier notifier,
            Supplier<Stage> stageSupplier,
            BiConsumer<String, String> alertHandler) {
        this.viewModel = viewModel;
        this.conversations = conversations;
        this.notifier = notifier;
        this.stageSupplier = stageSupplier;
        this.alertHandler = alertHandler;
        this.errorListener = (observable, oldValue, value) -> showAndClear(
                "Lỗi", value, viewModel.errorMessageProperty()::set);
        this.notificationListener = (observable, oldValue, value) -> showAndClear(
                "Thông báo", value, viewModel.notificationMessageProperty()::set);
        this.messageListener = (observable, oldValue, event) -> showNewMessage(event);
    }

    public void initialize() {
        viewModel.errorMessageProperty().addListener(errorListener);
        viewModel.notificationMessageProperty().addListener(notificationListener);
        viewModel.newMessageEventProperty().addListener(messageListener);
    }

    private void showNewMessage(ChatViewModel.NewMessageEvent event) {
        Stage stage = stageSupplier.get();
        if (event == null || stage == null) {
            return;
        }
        boolean viewingConversation = viewModel.activeConversationProperty().get() != null
                && event.conversationId().equals(viewModel.activeConversationProperty().get().getId());
        if (stage.isFocused() && viewingConversation) {
            return;
        }
        notifier.show(stage, event, () -> conversations.open(event));
    }

    private void showAndClear(
            String title, String value, java.util.function.Consumer<String> clearAction) {
        if (value == null || value.isBlank()) {
            return;
        }
        alertHandler.accept(title, value);
        clearAction.accept(null);
    }

    @Override
    public void close() {
        viewModel.errorMessageProperty().removeListener(errorListener);
        viewModel.notificationMessageProperty().removeListener(notificationListener);
        viewModel.newMessageEventProperty().removeListener(messageListener);
        notifier.close();
    }
}
