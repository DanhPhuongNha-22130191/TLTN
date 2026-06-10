package secretchat.chat.view;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import secretchat.chat.viewmodel.ChatViewModel;

public final class ChatSearchHandler {

    private final ChatViewModel viewModel;
    private final ChatMessagePane messagePane;
    private final TextField conversationSearchField;
    private final Label resultLabel;

    public ChatSearchHandler(
            ChatViewModel viewModel,
            ChatMessagePane messagePane,
            TextField conversationSearchField,
            Label resultLabel) {
        this.viewModel = viewModel;
        this.messagePane = messagePane;
        this.conversationSearchField = conversationSearchField;
        this.resultLabel = resultLabel;
    }

    public void searchConversations(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.isEmpty()) {
            viewModel.loadData();
        } else {
            viewModel.performSearch(normalized);
        }
    }

    public void searchMessages() {
        String keyword = conversationSearchField.getText();
        if (keyword == null || keyword.isBlank()) {
            clearResult();
            return;
        }

        ChatViewModel.MessageItem result = viewModel.searchConversationMessage(keyword);
        if (result == null) {
            showNotFound();
            return;
        }

        resultLabel.setText("Đã tìm thấy: " + result.getSenderName()
                + (result.getTime().isBlank() ? "" : " • " + result.getTime()));
        resultLabel.getStyleClass().remove("search-result-error");
        showResult();
        messagePane.scrollTo(result);
    }

    public void reset() {
        conversationSearchField.clear();
        clearResult();
    }

    public void clearResult() {
        resultLabel.setVisible(false);
        resultLabel.setManaged(false);
    }

    private void showNotFound() {
        resultLabel.setText("Không tìm thấy tin nhắn phù hợp");
        if (!resultLabel.getStyleClass().contains("search-result-error")) {
            resultLabel.getStyleClass().add("search-result-error");
        }
        showResult();
    }

    private void showResult() {
        resultLabel.setVisible(true);
        resultLabel.setManaged(true);
    }
}
