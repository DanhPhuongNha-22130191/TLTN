package secretchat.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import secretchat.config.GatewayConfig;
import secretchat.service.ApiClient;
import secretchat.service.SessionManager;

import java.time.Duration;
import java.util.Map;

public class AIService {

    private final ApiClient apiClient;

    public AIService() {
        this(ApiClient.getInstance());
    }

    AIService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    private static final System.Logger LOGGER = System.getLogger(AIService.class.getName());

    public String callAIAssistant(String query) {
        try {
            String accessToken = SessionManager.getInstance().getAccessToken();
            JsonNode response = apiClient.postAbsolute(
                    GatewayConfig.getInstance().getAiUrl(),
                    Map.of("question", query),
                    accessToken,
                    JsonNode.class,
                    Duration.ofSeconds(60));
            return response.path("answer").asText("Không nhận được câu trả lời từ AI.");
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Không thể kết nối tới Trợ lý AI", e);
            return "Không thể nhận phản hồi từ Trợ lý AI: " + e.getMessage();
        }
    }
}
