package secretchat.chat.viewmodel;

import javafx.application.Platform;
import secretchat.chat.service.ChatService;
import secretchat.dto.request.UpdateMessageRequest;
import secretchat.dto.response.MessageResponse;
import secretchat.util.IdUtils;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Handles mutations applied to existing messages.
 */
final class ChatMessageActionCoordinator {
    private final ChatService chatService;
    private final Supplier<String> tokenSupplier;
    private final Supplier<String> currentUserIdSupplier;
    private final Consumer<String> errorConsumer;
    private final Consumer<ChatViewModel.MessageItem> pinnedStateConsumer;
    private final Function<String, ChatViewModel.MessageItem> messageFinder;

    ChatMessageActionCoordinator(
            ChatService chatService,
            Supplier<String> tokenSupplier,
            Supplier<String> currentUserIdSupplier,
            Consumer<String> errorConsumer,
            Consumer<ChatViewModel.MessageItem> pinnedStateConsumer,
            Function<String, ChatViewModel.MessageItem> messageFinder) {
        this.chatService = chatService;
        this.tokenSupplier = tokenSupplier;
        this.currentUserIdSupplier = currentUserIdSupplier;
        this.errorConsumer = errorConsumer;
        this.pinnedStateConsumer = pinnedStateConsumer;
        this.messageFinder = messageFinder;
    }

    void deleteForUser(MessageResponse message, ChatViewModel.MessageItem item) {
        try {
            chatService.deleteMessageForUser(
                    IdUtils.parseLongId(message.getId()),
                    currentUserIdSupplier.get(),
                    tokenSupplier.get());
            Platform.runLater(() -> item.setDeletedForMe(true));
        } catch (Exception error) {
            errorConsumer.accept("Không thể xóa tin nhắn: " + error.getMessage());
        }
    }

    void recall(MessageResponse message, ChatViewModel.MessageItem item) {
        try {
            chatService.recallMessage(
                    IdUtils.parseLongId(message.getId()),
                    currentUserIdSupplier.get(),
                    tokenSupplier.get());
            Platform.runLater(() -> item.setDeleted(true));
        } catch (Exception error) {
            errorConsumer.accept("Không thể thu hồi tin nhắn: " + error.getMessage());
        }
    }

    void edit(ChatViewModel.MessageItem item, String content) {
        if (item == null || item.getResponse() == null) return;
        CompletableFuture.runAsync(() -> {
            try {
                UpdateMessageRequest request = new UpdateMessageRequest();
                request.setUserId(currentUserIdSupplier.get());
                request.setContent(content);
                chatService.editMessage(
                        IdUtils.parseLongId(item.getResponse().getId()),
                        request,
                        tokenSupplier.get());
            } catch (Exception error) {
                Platform.runLater(() -> errorConsumer.accept(
                        "Không thể chỉnh sửa tin nhắn: " + error.getMessage()));
            }
        });
    }

    void toggleStar(ChatViewModel.MessageItem item) {
        if (item == null || item.getResponse() == null) return;
        CompletableFuture.runAsync(() -> {
            try {
                chatService.setMessageStarred(
                        IdUtils.parseLongId(item.getResponse().getId()),
                        !item.isStarred(),
                        tokenSupplier.get());
            } catch (Exception error) {
                Platform.runLater(() -> errorConsumer.accept(
                        "Không thể đánh dấu sao: " + error.getMessage()));
            }
        });
    }

    void togglePin(ChatViewModel.MessageItem item) {
        if (item == null || item.getResponse() == null) return;
        boolean previous = item.isPinned();
        boolean target = !previous;
        Platform.runLater(() -> applyPinned(item, target));
        CompletableFuture.runAsync(() -> {
            try {
                chatService.setMessagePinned(
                        IdUtils.parseLongId(item.getResponse().getId()),
                        target,
                        tokenSupplier.get());
                Platform.runLater(() -> applyPinned(item, target));
            } catch (Exception error) {
                Platform.runLater(() -> {
                    applyPinned(item, previous);
                    errorConsumer.accept(
                            "Không thể cập nhật ghim tin nhắn: " + error.getMessage());
                });
            }
        });
    }

    void unpin(ChatViewModel.PinnedMessageItem pinned) {
        if (pinned == null || pinned.message() == null || !pinned.message().isPinned()) return;
        ChatViewModel.MessageItem item = pinned.message();
        CompletableFuture.runAsync(() -> {
            try {
                chatService.setMessagePinned(
                        IdUtils.parseLongId(pinned.messageId()),
                        false,
                        tokenSupplier.get());
                Platform.runLater(() -> {
                    applyPinned(item, false);
                    ChatViewModel.MessageItem loaded = messageFinder.apply(pinned.messageId());
                    if (loaded != null) loaded.setPinned(false);
                });
            } catch (Exception error) {
                Platform.runLater(() -> errorConsumer.accept(
                        "Không thể bỏ ghim tin nhắn: " + error.getMessage()));
            }
        });
    }

    private void applyPinned(ChatViewModel.MessageItem item, boolean pinned) {
        item.setPinned(pinned);
        pinnedStateConsumer.accept(item);
    }
}
