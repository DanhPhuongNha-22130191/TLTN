package secretchat.chat.service;

import secretchat.service.SessionManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class PresenceHeartbeatService implements AutoCloseable {
    private static final System.Logger LOGGER =
            System.getLogger(PresenceHeartbeatService.class.getName());

    private final ChatService chatService;
    private final ScheduledExecutorService scheduler;
    private volatile boolean started;

    public PresenceHeartbeatService(ChatService chatService) {
        this.chatService = chatService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "presence-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized void start() {
        if (started) return;
        started = true;
        scheduler.scheduleWithFixedDelay(this::sendHeartbeat, 0, 30, TimeUnit.SECONDS);
    }

    private void sendHeartbeat() {
        String token = SessionManager.getInstance().getAccessToken();
        if (token == null || token.isBlank()) return;
        try {
            chatService.sendPresenceHeartbeat(token);
        } catch (Exception error) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Không thể gửi presence heartbeat", error);
        }
    }

    @Override
    public synchronized void close() {
        scheduler.shutdownNow();
        started = false;
    }
}
