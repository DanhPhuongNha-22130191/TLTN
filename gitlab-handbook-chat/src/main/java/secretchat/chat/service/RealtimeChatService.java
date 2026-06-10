package secretchat.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import secretchat.config.GatewayConfig;
import secretchat.dto.request.SendMessageRequest;
import secretchat.dto.response.MessageResponse;
import secretchat.dto.response.FriendResponse;
import secretchat.dto.request.TypingRequest;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class RealtimeChatService implements AutoCloseable {

    private static final System.Logger LOGGER = System.getLogger(RealtimeChatService.class.getName());
    private static final Duration MAX_RECONNECT_DELAY = Duration.ofSeconds(30);

    private final ObjectMapper mapper = new ObjectMapper();
    private final RealtimeTransport stompClient;
    private final ScheduledExecutorService reconnectExecutor;
    private final Map<String, Subscription> desiredSubscriptions = new LinkedHashMap<>();
    private final Map<String, String> activeSubscriptionIds = new LinkedHashMap<>();
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean();

    private volatile CompletableFuture<Void> connection = CompletableFuture.failedFuture(
            new IllegalStateException("WebSocket has not been initialized"));
    private volatile String webSocketUrl;
    private volatile String accessToken;
    private volatile boolean closed;
    private int reconnectAttempt;

    public RealtimeChatService() {
        this(new StompWebSocketClient(), Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "secretchat-realtime-reconnect");
            thread.setDaemon(true);
            return thread;
        }));
    }

    RealtimeChatService(RealtimeTransport stompClient, ScheduledExecutorService reconnectExecutor) {
        this.stompClient = stompClient;
        this.reconnectExecutor = reconnectExecutor;
        stompClient.setDisconnectHandler(this::handleDisconnect);
    }

    public CompletableFuture<Void> connect(String accessToken) {
        this.webSocketUrl = GatewayConfig.getInstance().getWebSocketUrl();
        this.accessToken = accessToken;
        this.closed = false;
        return connectNow();
    }

    public CompletableFuture<Void> subscribe(
            String conversationId, Consumer<MessageResponse> messageHandler) {
        String destination = "/topic/conversation/" + conversationId;
        return subscribeJson(destination, destination,
                MessageResponse.class, messageHandler);
    }

    public CompletableFuture<Void> subscribeUserMessages(
            String userId, Consumer<MessageResponse> messageHandler) {
        String destination = "/topic/user/" + userId + "/messages";
        return subscribeJson(destination, destination,
                MessageResponse.class, messageHandler);
    }

    public CompletableFuture<Void> subscribeTyping(
            String conversationId, Consumer<TypingRequest> handler) {
        return subscribeJson("typing", "/topic/conversation/" + conversationId + "/typing",
                TypingRequest.class, handler);
    }

    public CompletableFuture<Void> subscribeFriends(
            String userId, Consumer<FriendResponse> handler) {
        String destination = "/topic/user/" + userId + "/friends";
        return subscribeJson(destination, destination,
                FriendResponse.class, handler);
    }

    public CompletableFuture<Void> sendMessage(SendMessageRequest request) {
        return sendJson("/app/chat.sendMessage", request);
    }

    public CompletableFuture<Void> sendTyping(TypingRequest request) {
        return sendJson("/app/chat.typing", request);
    }

    private CompletableFuture<Void> sendJson(String destination, Object request) {
        return awaitConnection().thenCompose(ignored -> {
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
        closed = true;
        activeSubscriptionIds.values().forEach(stompClient::unsubscribe);
        activeSubscriptionIds.clear();
        desiredSubscriptions.clear();
        stompClient.close();
        reconnectExecutor.shutdownNow();
    }

    private <T> CompletableFuture<Void> subscribeJson(
            String key, String destination, Class<T> type, Consumer<T> handler) {
        Consumer<String> payloadHandler = payload -> {
            try {
                handler.accept(mapper.readValue(payload, type));
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.ERROR, "Cannot parse WebSocket payload", e);
            }
        };
        synchronized (this) {
            desiredSubscriptions.put(key, new Subscription(destination, payloadHandler));
        }
        return awaitConnection().thenRun(() -> activateSubscription(key));
    }

    private synchronized CompletableFuture<Void> connectNow() {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Realtime service is closed"));
        }
        if (webSocketUrl == null || webSocketUrl.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("WebSocket URL is not configured"));
        }
        if (stompClient.isConnected()) {
            return CompletableFuture.completedFuture(null);
        }

        connection = stompClient.connect(webSocketUrl, accessToken)
                .thenRun(() -> {
                    synchronized (this) {
                        reconnectAttempt = 0;
                        reconnectScheduled.set(false);
                        activeSubscriptionIds.clear();
                        desiredSubscriptions.keySet().forEach(this::activateSubscription);
                    }
                });
        connection.whenComplete((ignored, error) -> {
            if (error != null) {
                scheduleReconnect(error);
            }
        });
        return connection;
    }

    private CompletableFuture<Void> awaitConnection() {
        if (stompClient.isConnected()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> current = connection;
        if (current != null && !current.isDone()) {
            return current;
        }
        return connectNow();
    }

    private synchronized void activateSubscription(String key) {
        if (!stompClient.isConnected()) {
            return;
        }
        Subscription subscription = desiredSubscriptions.get(key);
        if (subscription == null) {
            return;
        }
        stompClient.unsubscribe(activeSubscriptionIds.remove(key));
        String id = stompClient.subscribe(subscription.destination(), subscription.handler());
        activeSubscriptionIds.put(key, id);
    }

    private void handleDisconnect(Throwable error) {
        synchronized (this) {
            activeSubscriptionIds.clear();
        }
        scheduleReconnect(error);
    }

    private void scheduleReconnect(Throwable error) {
        if (closed || reconnectExecutor.isShutdown() || !reconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        int attempt;
        synchronized (this) {
            attempt = reconnectAttempt++;
        }
        long delaySeconds = Math.min(MAX_RECONNECT_DELAY.toSeconds(), 1L << Math.min(attempt, 5));
        LOGGER.log(System.Logger.Level.WARNING,
                "Realtime connection lost; reconnecting in " + delaySeconds
                        + " seconds: " + rootMessage(error));
        reconnectExecutor.schedule(() -> {
            reconnectScheduled.set(false);
            connectNow();
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record Subscription(String destination, Consumer<String> handler) {
    }
}
