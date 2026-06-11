package secretchat.userservice.infrastructure.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import secretchat.userservice.application.port.MailboxPort;
import secretchat.userservice.domain.exception.MailboxException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DockerMailserverMailboxAdapter implements MailboxPort {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(DockerMailserverMailboxAdapter.class);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${mail.internal.api-url}")
    private String apiUrl;

    @Value("${mail.internal.api-token}")
    private String apiToken;

    @Value("${mail.internal.quota-bytes:104857600}")
    private long quotaBytes;

    @Override
    public void createMailbox(String email, String password, String displayName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("displayName", displayName == null ? email : displayName);
        body.put("quotaBytes", quotaBytes);

        send(HttpRequest.newBuilder()
                .uri(URI.create(normalizedApiUrl() + "/accounts"))
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body))));
    }

    @Override
    public void deleteMailbox(String email) {
        String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
        send(HttpRequest.newBuilder()
                .uri(URI.create(normalizedApiUrl() + "/accounts/" + encodedEmail))
                .header("Authorization", "Bearer " + apiToken)
                .timeout(Duration.ofSeconds(20))
                .DELETE());
    }

    private String normalizedApiUrl() {
        return apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new MailboxException("Không thể tạo yêu cầu quản lý hộp thư", error);
        }
    }

    private void send(HttpRequest.Builder requestBuilder) {
        HttpRequest request = requestBuilder.build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            LOGGER.info("Mail account manager response: method={}, uri={}, status={}",
                    request.method(), request.uri(), response.statusCode());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.error(
                        "Mail account manager request failed: method={}, uri={}, status={}, response={}",
                        request.method(), request.uri(), response.statusCode(), response.body());
                throw new MailboxException(
                        "Mail account manager trả về HTTP "
                                + response.statusCode() + ": " + response.body());
            }
        } catch (MailboxException error) {
            throw error;
        } catch (Exception error) {
            LOGGER.error("Mail account manager connection failed: method={}, uri={}",
                    request.method(), request.uri(), error);
            throw new MailboxException("Không thể kết nối mail account manager", error);
        }
    }
}
