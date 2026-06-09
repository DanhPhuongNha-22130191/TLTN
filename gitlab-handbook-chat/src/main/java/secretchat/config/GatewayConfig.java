package secretchat.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class GatewayConfig {
    private static final System.Logger LOGGER = System.getLogger(GatewayConfig.class.getName());
    private static final GatewayConfig INSTANCE = new GatewayConfig();
    private String gatewayUrl;

    private GatewayConfig() {
        loadConfig();
    }

    private void loadConfig() {
        io.github.cdimascio.dotenv.Dotenv dotenv = io.github.cdimascio.dotenv.Dotenv.configure().ignoreIfMissing()
                .load();

        // Ưu tiên 1: Environment variable (Docker/K8s/Dotenv)
        String port = System.getenv("GATEWAY_PORT");
        if (port == null)
            port = dotenv.get("GATEWAY_PORT");

        String host = System.getenv("GATEWAY_HOST");
        if (host == null)
            host = dotenv.get("GATEWAY_HOST");

        // Ưu tiên 2: System property (java -Dgateway.port=...)
        if (port == null) {
            port = System.getProperty("gateway.port");
        }
        if (host == null) {
            host = System.getProperty("gateway.host");
        }

        // Ưu tiên 3: Config file (gateway-config.properties)
        if (port == null || host == null) {
            Properties props = loadProperties();
            port = port != null ? port : props.getProperty("gateway.port");
            host = host != null ? host : props.getProperty("gateway.host");
        }

        // Fallback mặc định
        String finalHost = host != null ? host : "localhost";
        String finalPort = port != null ? port : "8088";

        this.gatewayUrl = "http://" + finalHost + ":" + finalPort;
    }

    private Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream input = getClass()
                .getClassLoader()
                .getResourceAsStream("gateway-config.properties")) {
            if (input != null) {
                props.load(input);
            }
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.WARNING, "Cannot load gateway-config.properties, using defaults", e);
        }
        return props;
    }

    public static GatewayConfig getInstance() {
        return INSTANCE;
    }

    public String getGatewayUrl() {
        return gatewayUrl;
    }

    public String getAuthUrl(String endpoint) {
        return gatewayUrl + "/api/auth" + endpoint;
    }

    public String getUserUrl(String endpoint) {
        return gatewayUrl + "/api/users" + endpoint;
    }

    public String getMessageUrl(String endpoint) {
        return gatewayUrl + "/api/messages" + endpoint;
    }

    public String getAiUrl() {
        return gatewayUrl + "/api/ai/chat";
    }

    public String getWebSocketUrl() {
        return gatewayUrl.replaceFirst("^http", "ws") + "/ws";
    }
}
