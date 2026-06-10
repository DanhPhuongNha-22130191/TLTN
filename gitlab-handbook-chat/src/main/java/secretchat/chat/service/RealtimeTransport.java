package secretchat.chat.service;

import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

interface RealtimeTransport extends AutoCloseable {

    CompletableFuture<Void> connect(String url, String accessToken);

    String subscribe(String destination, Consumer<String> messageHandler);

    void unsubscribe(String subscriptionId);

    CompletableFuture<WebSocket> send(String destination, String jsonBody);

    boolean isConnected();

    void setDisconnectHandler(Consumer<Throwable> handler);

    @Override
    void close();
}
