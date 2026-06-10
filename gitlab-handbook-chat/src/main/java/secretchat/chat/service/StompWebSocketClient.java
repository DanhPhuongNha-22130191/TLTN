package secretchat.chat.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class StompWebSocketClient implements RealtimeTransport, WebSocket.Listener {

    private static final System.Logger LOGGER = System.getLogger(StompWebSocketClient.class.getName());

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final Map<String, Consumer<String>> subscriptions = new ConcurrentHashMap<>();
    private final StringBuilder incoming = new StringBuilder();
    private final AtomicReference<CompletableFuture<Void>> connectFuture = new AtomicReference<>();

    private volatile WebSocket webSocket;
    private volatile Consumer<Throwable> disconnectHandler = ignored -> {};
    private volatile boolean closing;

    @Override
    public CompletableFuture<Void> connect(String url, String accessToken) {
        closing = false;
        WebSocket current = webSocket;
        CompletableFuture<Void> pending = connectFuture.get();
        if (current != null && !current.isOutputClosed() && pending != null) {
            return pending;
        }

        CompletableFuture<Void> result = new CompletableFuture<>();
        connectFuture.set(result);

        WebSocket.Builder builder = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10));
        if (accessToken != null && !accessToken.isBlank()) {
            builder.header("Authorization", "Bearer " + accessToken);
        }

        builder.buildAsync(URI.create(url), this)
                .whenComplete((socket, error) -> {
                    if (error != null) {
                        connectFuture.compareAndSet(result, null);
                        result.completeExceptionally(error);
                    } else {
                        webSocket = socket;
                        Map<String, String> headers = new HashMap<>();
                        headers.put("accept-version", "1.2");
                        headers.put("heart-beat", "0,0");
                        if (accessToken != null && !accessToken.isBlank()) {
                            headers.put("Authorization", "Bearer " + accessToken);
                        }
                        sendFrame("CONNECT", headers, "");
                    }
                });
        return result;
    }

    @Override
    public String subscribe(String destination, Consumer<String> messageHandler) {
        requireConnected();
        String subscriptionId = UUID.randomUUID().toString();
        subscriptions.put(subscriptionId, messageHandler);
        sendFrame("SUBSCRIBE", Map.of(
                "id", subscriptionId,
                "destination", destination,
                "ack", "auto"
        ), "");
        return subscriptionId;
    }

    @Override
    public void unsubscribe(String subscriptionId) {
        if (subscriptionId == null) {
            return;
        }
        subscriptions.remove(subscriptionId);
        if (isConnected()) {
            sendFrame("UNSUBSCRIBE", Map.of("id", subscriptionId), "");
        }
    }

    @Override
    public CompletableFuture<WebSocket> send(String destination, String jsonBody) {
        requireConnected();
        return sendFrame("SEND", Map.of(
                "destination", destination,
                "content-type", "application/json",
                "content-length", String.valueOf(jsonBody.getBytes(StandardCharsets.UTF_8).length)
        ), jsonBody);
    }

    @Override
    public boolean isConnected() {
        WebSocket current = webSocket;
        CompletableFuture<Void> future = connectFuture.get();
        return current != null && !current.isOutputClosed() && future != null && future.isDone()
                && !future.isCompletedExceptionally();
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        this.webSocket = webSocket;
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        synchronized (incoming) {
            incoming.append(data);
            if (last) {
                processIncomingFrames();
            }
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        if (this.webSocket == webSocket) {
            this.webSocket = null;
        }
        subscriptions.clear();
        IllegalStateException error = new IllegalStateException(
                "WebSocket closed: " + statusCode + (reason.isBlank() ? "" : " - " + reason));
        failPendingConnection(error);
        notifyDisconnected(error);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        if (this.webSocket == webSocket) {
            this.webSocket = null;
        }
        subscriptions.clear();
        failPendingConnection(error);
        LOGGER.log(System.Logger.Level.ERROR, "STOMP WebSocket error", error);
        notifyDisconnected(error);
    }

    @Override
    public void close() {
        closing = true;
        WebSocket current = webSocket;
        subscriptions.clear();
        if (current != null && !current.isOutputClosed()) {
            sendFrame(current, "DISCONNECT", Map.of(), "");
            current.sendClose(WebSocket.NORMAL_CLOSURE, "Client closed");
        }
        webSocket = null;
    }

    @Override
    public void setDisconnectHandler(Consumer<Throwable> handler) {
        disconnectHandler = handler == null ? ignored -> {} : handler;
    }

    private void processIncomingFrames() {
        int terminator;
        while ((terminator = incoming.indexOf("\0")) >= 0) {
            String rawFrame = incoming.substring(0, terminator);
            incoming.delete(0, terminator + 1);
            handleFrame(rawFrame.stripLeading());
        }

        if ("\n".contentEquals(incoming)) {
            incoming.setLength(0);
        }
    }

    private void handleFrame(String rawFrame) {
        if (rawFrame.isBlank()) {
            return;
        }

        rawFrame = rawFrame.replace("\r\n", "\n");
        int separator = rawFrame.indexOf("\n\n");
        String headerBlock = separator >= 0 ? rawFrame.substring(0, separator) : rawFrame;
        String body = separator >= 0 ? rawFrame.substring(separator + 2) : "";
        String[] lines = headerBlock.split("\n");
        String command = lines[0].trim();
        Map<String, String> headers = new ConcurrentHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                headers.put(lines[i].substring(0, colon), lines[i].substring(colon + 1));
            }
        }

        if ("CONNECTED".equals(command)) {
            CompletableFuture<Void> future = connectFuture.get();
            if (future != null) {
                future.complete(null);
            }
        } else if ("MESSAGE".equals(command)) {
            Consumer<String> handler = subscriptions.get(headers.get("subscription"));
            if (handler != null) {
                handler.accept(body);
            }
        } else if ("ERROR".equals(command)) {
            IllegalStateException error = new IllegalStateException(
                    headers.getOrDefault("message", "STOMP server error") + ": " + body);
            failPendingConnection(error);
            LOGGER.log(System.Logger.Level.ERROR, error.getMessage());
        }
    }

    private CompletableFuture<WebSocket> sendFrame(String command, Map<String, String> headers, String body) {
        return sendFrame(requireSocket(), command, headers, body);
    }

    private CompletableFuture<WebSocket> sendFrame(
            WebSocket socket, String command, Map<String, String> headers, String body) {
        StringBuilder frame = new StringBuilder(command).append('\n');
        headers.forEach((name, value) -> frame.append(name).append(':').append(value).append('\n'));
        frame.append('\n').append(body == null ? "" : body).append('\0');
        return socket.sendText(frame, true);
    }

    private WebSocket requireSocket() {
        WebSocket current = webSocket;
        if (current == null || current.isOutputClosed()) {
            throw new IllegalStateException("WebSocket is not connected");
        }
        return current;
    }

    private void requireConnected() {
        if (!isConnected()) {
            throw new IllegalStateException("STOMP session is not connected");
        }
    }

    private void failPendingConnection(Throwable error) {
        CompletableFuture<Void> future = connectFuture.get();
        if (future != null && !future.isDone()) {
            future.completeExceptionally(error);
        }
    }

    private void notifyDisconnected(Throwable error) {
        if (!closing) {
            disconnectHandler.accept(error);
        }
    }
}
