package secretchat.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class GatewayConfig {
    private static final System.Logger LOGGER = System.getLogger(GatewayConfig.class.getName());
    private static final GatewayConfig INSTANCE = new GatewayConfig();
    private String gatewayUrl;

    private GatewayConfig() {
        loadConfig();
    }

    private void loadConfig() {
        io.github.cdimascio.dotenv.Dotenv dotenv = loadDotenv();

        // Priority 1: OS environment variables.
        String port = System.getenv("GATEWAY_PORT");
        if (port == null)
            port = dotenv.get("GATEWAY_PORT");

        String host = System.getenv("GATEWAY_HOST");
        if (host == null)
            host = dotenv.get("GATEWAY_HOST");

        String scheme = System.getenv("GATEWAY_SCHEME");
        if (scheme == null)
            scheme = dotenv.get("GATEWAY_SCHEME");

        // Priority 2: System properties (java -Dgateway.port=...).
        if (port == null) {
            port = System.getProperty("gateway.port");
        }
        if (host == null) {
            host = System.getProperty("gateway.host");
        }
        if (scheme == null) {
            scheme = System.getProperty("gateway.scheme");
        }

        // Priority 3: Packaged defaults.
        if (port == null || host == null) {
            Properties props = loadProperties();
            port = port != null ? port : props.getProperty("gateway.port");
            host = host != null ? host : props.getProperty("gateway.host");
            scheme = scheme != null ? scheme : props.getProperty("gateway.scheme");
        }

        // Final fallback.
        String finalHost = host != null ? host : "localhost";
        String finalPort = port != null ? port : "8088";
        String finalScheme = scheme != null ? scheme : "https";

        this.gatewayUrl = finalScheme + "://" + finalHost + ":" + finalPort;
    }

    private io.github.cdimascio.dotenv.Dotenv loadDotenv() {
        Path envDirectory = findEnvDirectory();
        if (envDirectory != null) {
            LOGGER.log(System.Logger.Level.INFO, "Loading gateway configuration from {0}",
                    envDirectory.resolve(".env"));
            return io.github.cdimascio.dotenv.Dotenv.configure()
                    .directory(envDirectory.toString())
                    .ignoreIfMissing()
                    .load();
        }
        return io.github.cdimascio.dotenv.Dotenv.configure()
                .ignoreIfMissing()
                .load();
    }

    private Path findEnvDirectory() {
        String launcherPath = System.getProperty("jpackage.app-path");
        if (launcherPath != null && !launcherPath.isBlank()) {
            Path launcherDirectory = Path.of(launcherPath).toAbsolutePath().normalize().getParent();
            if (launcherDirectory != null && Files.isRegularFile(launcherDirectory.resolve(".env"))) {
                return launcherDirectory;
            }
        }

        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(workingDirectory.resolve(".env"))) {
            return workingDirectory;
        }
        return null;
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
