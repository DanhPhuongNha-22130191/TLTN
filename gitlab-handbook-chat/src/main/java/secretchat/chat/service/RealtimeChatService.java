package secretchat.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import secretchat.config.GatewayConfig;
import secretchat.dto.request.SendMessageRequest;
import secretchat.dto.response.MessageResponse;
import secretchat.dto.response.FriendResponse;
import secretchat.dto.request.TypingRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class RealtimeChatService implements AutoCloseable {

    private final ObjectMapper mapper = new ObjectMapper();
    private final StompWebSocketClient stompClient = new StompWebSocketClient();
    private CompletableFuture<Void> connection = CompletableFuture.failedFuture(
            new IllegalStateException("WebSocket has not been initialized"));
    private final Map<String, String> subscriptionIds = new HashMap<>();

    public CompletableFuture<Void> connect(String accessToken) {
        connection = stompClient.connect(GatewayConfig.getInstance().getWebSocketUrl(), accessToken);
        return connection;
    }

    public CompletableFuture<Void> subscribe(
            String conversationId, Consumer<MessageResponse> messageHandler) {
        return subscribeJson("messages", "/topic/conversation/" + conversationId,
                MessageResponse.class, messageHandler);
    }

    public CompletableFuture<Void> subscribeUserMessages(
            String userId, Consumer<MessageResponse> messageHandler) {
        return subscribeJson("messages", "/topic/user/" + userId + "/messages",
                MessageResponse.class, messageHandler);
    }

    public CompletableFuture<Void> subscribeTyping(
            String conversationId, Consumer<TypingRequest> handler) {
        return subscribeJson("typing", "/topic/conversation/" + conversationId + "/typing",
                TypingRequest.class, handler);
    }

    public CompletableFuture<Void> subscribeFriends(
            String userId, Consumer<FriendResponse> handler) {
        return subscribeJson("friends", "/topic/user/" + userId + "/friends",
                FriendResponse.class, handler);
    }

    public CompletableFuture<Void> sendMessage(SendMessageRequest request) {
        return sendJson("/app/chat.sendMessage", request);
    }

    public CompletableFuture<Void> sendTyping(TypingRequest request) {
        return sendJson("/app/chat.typing", request);
    }

    private CompletableFuture<Void> sendJson(String destination, Object request) {
        return connection.thenCompose(ignored -> {
            try {
                String json = mapper.writeValueAsString(request);
                return stompClient.send(destination, json).thenApply(socket -> null);
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        });
    }

    @Override
    public synchronized void close() {
        subscriptionIds.values().forEach(stompClient::unsubscribe);
        subscriptionIds.clear();
        stompClient.close();
    }

    private <T> CompletableFuture<Void> subscribeJson(
            String key, String destination, Class<T> type, Consumer<T> handler) {
        return connection.thenRun(() -> {
            synchronized (this) {
                stompClient.unsubscribe(subscriptionIds.remove(key));
                String id = stompClient.subscribe(destination, payload -> {
                    try {
                        handler.accept(mapper.readValue(payload, type));
                    } catch (Exception e) {
                        System.getLogger(RealtimeChatService.class.getName())
                                .log(System.Logger.Level.ERROR, "Cannot parse WebSocket payload", e);
                    }
                });
                subscriptionIds.put(key, id);
            }
        });
    }
}
