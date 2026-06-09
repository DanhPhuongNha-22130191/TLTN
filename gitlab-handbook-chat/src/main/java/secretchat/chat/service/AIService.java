package secretchat.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import secretchat.config.GatewayConfig;
import secretchat.service.SessionManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class AIService {

    private final ObjectMapper mapper;
    private final HttpClient client;

    public AIService() {
        this.mapper = new ObjectMapper();
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private static final System.Logger LOGGER = System.getLogger(AIService.class.getName());

    public String callAIAssistant(String query) {
        try {
            var reqNode = mapper.createObjectNode();
            reqNode.put("question", query);
            String jsonBody = mapper.writeValueAsString(reqNode);

            String aiUrl = GatewayConfig.getInstance().getAiUrl();

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(aiUrl))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            String accessToken = SessionManager.getInstance().getAccessToken();
            if (accessToken != null && !accessToken.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + accessToken);
            }

            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var resNode = mapper.readTree(response.body());
                return resNode.path("answer").asText("Không nhận được câu trả lời từ AI.");
            } else {
                return "Lỗi từ máy chủ AI (Status code: " + response.statusCode() + ")";
            }
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Không thể kết nối tới Trợ lý AI", e);
            return "Không thể kết nối tới Trợ lý AI: " + e.getMessage();
        }
    }
}
