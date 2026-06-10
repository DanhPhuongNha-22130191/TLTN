package secretchat.chat.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.http.WebSocket;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealtimeChatServiceTest {

    private final FakeRealtimeTransport transport = new FakeRealtimeTransport();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final RealtimeChatService service = new RealtimeChatService(transport, executor);

    @AfterEach
    void tearDown() {
        service.close();
    }

    @Test
    void keepsUserAndConversationMessageSubscriptionsActiveTogether() {
        service.connect("token").join();
        service.subscribeUserMessages("user-1", ignored -> {}).join();
        service.subscribe("conversation-1", ignored -> {}).join();

        assertEquals(2, transport.activeDestinations().size());
        assertTrue(transport.activeDestinations().contains("/topic/user/user-1/messages"));
        assertTrue(transport.activeDestinations().contains("/topic/conversation/conversation-1"));
    }

    @Test
    void replacesTypingSubscriptionWhenConversationChanges() {
        service.connect("token").join();
        service.subscribeTyping("conversation-1", ignored -> {}).join();
        service.subscribeTyping("conversation-2", ignored -> {}).join();

        assertEquals(1, transport.activeDestinations().size());
        assertTrue(transport.activeDestinations()
                .contains("/topic/conversation/conversation-2/typing"));
    }

    @Test
    void restoresSubscriptionsAfterConnectionLoss() throws InterruptedException {
        service.connect("token").join();
        service.subscribeUserMessages("user-1", ignored -> {}).join();

        transport.disconnect(new IllegalStateException("network unavailable"));

        assertTrue(transport.subscriptionRestored.await(3, TimeUnit.SECONDS));
        assertTrue(transport.activeDestinations().contains("/topic/user/user-1/messages"));
    }

    private static final class FakeRealtimeTransport implements RealtimeTransport {
        private final Map<String, String> subscriptions = new LinkedHashMap<>();
        private final CountDownLatch subscriptionRestored = new CountDownLatch(1);
        private Consumer<Throwable> disconnectHandler = ignored -> {};
        private boolean connected;
        private int connectionCount;
        private int nextSubscriptionId;

        @Override
        public CompletableFuture<Void> connect(String url, String accessToken) {
            connected = true;
            connectionCount++;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public String subscribe(String destination, Consumer<String> messageHandler) {
            String id = "subscription-" + nextSubscriptionId++;
            subscriptions.put(id, destination);
            if (connectionCount > 1) {
                subscriptionRestored.countDown();
            }
            return id;
        }

        @Override
        public void unsubscribe(String subscriptionId) {
            subscriptions.remove(subscriptionId);
        }

        @Override
        public CompletableFuture<WebSocket> send(String destination, String jsonBody) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public void setDisconnectHandler(Consumer<Throwable> handler) {
            disconnectHandler = handler;
        }

        @Override
        public void close() {
            connected = false;
            subscriptions.clear();
        }

        void disconnect(Throwable error) {
            connected = false;
            subscriptions.clear();
            disconnectHandler.accept(error);
        }

        java.util.Collection<String> activeDestinations() {
            return subscriptions.values();
        }
    }
}
