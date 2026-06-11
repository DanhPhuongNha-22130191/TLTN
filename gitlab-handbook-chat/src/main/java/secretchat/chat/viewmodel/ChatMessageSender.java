package secretchat.chat.viewmodel;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import secretchat.chat.service.AIService;
import secretchat.chat.service.ChatService;
import secretchat.chat.service.RealtimeChatService;
import secretchat.dto.request.SendMessageRequest;
import secretchat.dto.response.ConversationResponse;
import secretchat.dto.response.MessageResponse;
import secretchat.dto.response.UserResponse;
import secretchat.util.FileUtils;
import secretchat.util.IdUtils;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Sends text, AI, and file messages while managing optimistic UI entries.
 */
final class ChatMessageSender {
    private static final System.Logger LOGGER =
            System.getLogger(ChatMessageSender.class.getName());
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024L;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ChatService chatService;
    private final AIService aiService;
    private final RealtimeChatService realtimeService;
    private final Supplier<String> tokenSupplier;
    private final Supplier<String> currentUserIdSupplier;
    private final Supplier<UserResponse> currentUserSupplier;
    private final Supplier<ConversationResponse> conversationSupplier;
    private final Supplier<String> chatNameSupplier;
    private final ObservableList<ChatViewModel.MessageItem> messages;
    private final Consumer<String> errorConsumer;
    private final Consumer<Boolean> aiLoadingConsumer;

    ChatMessageSender(
            ChatService chatService,
            AIService aiService,
            RealtimeChatService realtimeService,
            Supplier<String> tokenSupplier,
            Supplier<String> currentUserIdSupplier,
            Supplier<UserResponse> currentUserSupplier,
            Supplier<ConversationResponse> conversationSupplier,
            Supplier<String> chatNameSupplier,
            ObservableList<ChatViewModel.MessageItem> messages,
            Consumer<String> errorConsumer,
            Consumer<Boolean> aiLoadingConsumer) {
        this.chatService = chatService;
        this.aiService = aiService;
        this.realtimeService = realtimeService;
        this.tokenSupplier = tokenSupplier;
        this.currentUserIdSupplier = currentUserIdSupplier;
        this.currentUserSupplier = currentUserSupplier;
        this.conversationSupplier = conversationSupplier;
        this.chatNameSupplier = chatNameSupplier;
        this.messages = messages;
        this.errorConsumer = errorConsumer;
        this.aiLoadingConsumer = aiLoadingConsumer;
    }

    void send(String text, File file) {
        ConversationResponse conversation = conversationSupplier.get();
        if (conversation == null) {
            errorConsumer.accept("Vui lòng chọn cuộc trò chuyện trước.");
            return;
        }

        boolean hasText = text != null && !text.trim().isEmpty();
        boolean hasFile = file != null;
        if (!hasText && !hasFile) return;
        if (hasFile && file.length() > MAX_FILE_SIZE) {
            errorConsumer.accept("Kích thước file không được vượt quá 100 MB.");
            return;
        }

        boolean aiChat = "TRỢ LÝ AI".equals(chatNameSupplier.get());
        Long conversationId = IdUtils.parseLongId(conversation.getId());
        if (hasText) sendText(conversationId, text.trim(), aiChat);
        if (hasFile) {
            if (aiChat) {
                errorConsumer.accept("Trợ lý AI hiện chưa hỗ trợ nhận file.");
                return;
            }
            ChatViewModel.MessageItem pending = createPending(
                    conversationId, file.getName(), "FILE", file.getName(), file.length());
            CompletableFuture.runAsync(() -> uploadAndSend(conversationId, file, pending));
        }
    }

    private void sendText(Long conversationId, String text, boolean aiChat) {
        ChatViewModel.MessageItem pending = createPending(
                conversationId, text, "TEXT", null, null);
        realtimeService.sendMessage(textRequest(
                        conversationId, currentUserIdSupplier.get(), text))
                .thenRun(() -> {
                    Platform.runLater(() -> pending.setStatus("SENT"));
                    if (aiChat) sendAiResponse(conversationId, text);
                })
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        pending.setStatus("FAILED");
                        errorConsumer.accept(
                                "Không thể gửi tin nhắn qua WebSocket: "
                                        + rootMessage(error));
                    });
                    return null;
                });
    }

    private ChatViewModel.MessageItem createPending(
            Long conversationId,
            String content,
            String messageType,
            String fileName,
            Long fileSize) {
        MessageResponse response = new MessageResponse();
        response.setId("pending-" + UUID.randomUUID());
        response.setConversationId(String.valueOf(conversationId));
        response.setSenderId(currentUserIdSupplier.get());
        response.setContent(content);
        response.setMessageType(messageType);
        response.setFileName(fileName);
        response.setFileSize(fileSize);
        response.setStatus("SENDING");

        UserResponse currentUser = currentUserSupplier.get();
        ChatViewModel.MessageItem item = new ChatViewModel.MessageItem(
                response,
                currentUser == null ? "Bạn" : currentUser.getUsername(),
                content,
                LocalTime.now().format(TIME_FORMAT),
                true,
                !"TEXT".equalsIgnoreCase(messageType),
                false,
                false);
        if (!"TEXT".equalsIgnoreCase(messageType)) item.setUploadProgress(0);
        if (Platform.isFxApplicationThread()) messages.add(item);
        else Platform.runLater(() -> messages.add(item));
        return item;
    }

    private void sendAiResponse(Long conversationId, String question) {
        Platform.runLater(() -> aiLoadingConsumer.accept(true));
        CompletableFuture.runAsync(() -> {
            String answer = aiService.callAIAssistant(question);
            realtimeService.sendMessage(textRequest(conversationId, "AI_ASSISTANT", answer))
                    .whenComplete((ignored, error) ->
                            Platform.runLater(() -> aiLoadingConsumer.accept(false)))
                    .exceptionally(error -> {
                        Platform.runLater(() -> errorConsumer.accept(
                                "Không thể gửi phản hồi AI qua WebSocket: "
                                        + rootMessage(error)));
                        return null;
                    });
        });
    }

    private void uploadAndSend(
            Long conversationId, File file, ChatViewModel.MessageItem pending) {
        try {
            AtomicInteger lastPercent = new AtomicInteger(-1);
            String uploadedUrl = chatService.uploadFile(
                    file,
                    tokenSupplier.get(),
                    progress -> {
                        int percent = (int) Math.round(progress * 100);
                        if (lastPercent.getAndSet(percent) != percent) {
                            Platform.runLater(() ->
                                    pending.setUploadProgress(percent / 100d));
                        }
                    });

            SendMessageRequest request = new SendMessageRequest();
            request.setConversationId(conversationId);
            request.setSenderId(currentUserIdSupplier.get());
            request.setFileName(file.getName());
            request.setFileSize(file.length());
            request.setFileUrl(uploadedUrl);
            request.setFileType(FileUtils.getFileExtension(file));
            String extension = FileUtils.getFileExtension(file).toLowerCase();
            request.setMessageType(
                    extension.equals("png") || extension.equals("jpg")
                            || extension.equals("jpeg") || extension.equals("gif")
                            ? "IMAGE" : "FILE");

            realtimeService.sendMessage(request).join();
            Platform.runLater(() -> {
                pending.setUploadProgress(1);
                pending.setStatus("SENT");
            });
        } catch (Exception error) {
            LOGGER.log(System.Logger.Level.ERROR, "Lỗi gửi file", error);
            Platform.runLater(() -> {
                pending.setStatus("FAILED");
                errorConsumer.accept(
                        "Không thể gửi file qua WebSocket: " + rootMessage(error));
            });
        }
    }

    private SendMessageRequest textRequest(
            Long conversationId, String senderId, String content) {
        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(conversationId);
        request.setSenderId(senderId);
        request.setContent(content);
        request.setMessageType("TEXT");
        return request;
    }

    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() != null
                ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
