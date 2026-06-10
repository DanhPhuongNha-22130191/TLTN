package secretchat.userservice.infrastructure.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
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
public class MailuMailboxAdapter implements MailboxPort {
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
        body.put("raw_password", password);
        body.put("displayed_name", displayName == null ? email : displayName);
        body.put("quota_bytes", quotaBytes);
        body.put("enabled", true);
        body.put("change_pw_next_login", false);
        body.put("enable_imap", true);
        body.put("enable_pop", false);
        body.put("allow_spoofing", false);
        body.put("forward_enabled", false);
        body.put("reply_enabled", false);
        body.put("spam_enabled", true);

        send(HttpRequest.newBuilder()
                .uri(URI.create(normalizedApiUrl() + "/user"))
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body))));
    }

    @Override
    public void deleteMailbox(String email) {
        String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
        send(HttpRequest.newBuilder()
                .uri(URI.create(normalizedApiUrl() + "/user/" + encodedEmail))
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
            throw new MailboxException("Không thể tạo yêu cầu Mailu", error);
        }
    }

    private void send(HttpRequest.Builder requestBuilder) {
        try {
            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new MailboxException(
                        "Mailu trả về HTTP " + response.statusCode() + ": " + response.body());
            }
        } catch (MailboxException error) {
            throw error;
        } catch (Exception error) {
            throw new MailboxException("Không thể kết nối Mailu", error);
        }
    }
}
