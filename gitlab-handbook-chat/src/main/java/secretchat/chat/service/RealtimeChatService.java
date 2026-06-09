package secretchat.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import secretchat.config.GatewayConfig;
import secretchat.dto.request.SendMessageRequest;
import secretchat.dto.response.MessageResponse;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class RealtimeChatService implements AutoCloseable {

    private final ObjectMapper mapper = new ObjectMapper();
    private final StompWebSocketClient stompClient = new StompWebSocketClient();
    private CompletableFuture<Void> connection = CompletableFuture.failedFuture(
            new IllegalStateException("WebSocket has not been initialized"));
    private String activeSubscriptionId;

    public CompletableFuture<Void> connect(String accessToken) {
        connection = stompClient.connect(GatewayConfig.getInstance().getWebSocketUrl(), accessToken);
        return connection;
    }

    public CompletableFuture<Void> subscribe(
            String conversationId, Consumer<MessageResponse> messageHandler) {
        return connection.thenRun(() -> {
            synchronized (this) {
                stompClient.unsubscribe(activeSubscriptionId);
                activeSubscriptionId = stompClient.subscribe(
                        "/topic/conversation/" + conversationId,
                        payload -> deserializeMessage(payload, messageHandler));
            }
        });
    }

    public CompletableFuture<Void> sendMessage(SendMessageRequest request) {
        return connection.thenCompose(ignored -> {
            try {
                String json = mapper.writeValueAsString(request);
                return stompClient.send("/app/chat.sendMessage", json).thenApply(socket -> null);
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        });
    }

    @Override
    public synchronized void close() {
        stompClient.unsubscribe(activeSubscriptionId);
        activeSubscriptionId = null;
        stompClient.close();
    }

    private void deserializeMessage(String payload, Consumer<MessageResponse> messageHandler) {
        try {
            messageHandler.accept(mapper.readValue(payload, MessageResponse.class));
        } catch (Exception e) {
            System.getLogger(RealtimeChatService.class.getName())
                    .log(System.Logger.Level.ERROR, "Cannot parse WebSocket message", e);
        }
    }
}
