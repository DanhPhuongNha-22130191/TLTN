package secretchat.service;

import secretchat.config.GatewayConfig;
import secretchat.config.GatewayHealthChecker;

import java.util.concurrent.CompletableFuture;

public class ConnectionStatusService {

    public String getGatewayUrl() {
        return GatewayConfig.getInstance().getGatewayUrl();
    }

    public CompletableFuture<Boolean> checkGatewayAsync() {
        return CompletableFuture.supplyAsync(GatewayHealthChecker::isGatewayReachable);
    }
}
