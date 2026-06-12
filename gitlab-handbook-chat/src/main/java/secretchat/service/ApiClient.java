package secretchat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import secretchat.common.exception.ApiException;
import secretchat.common.exception.GlobalExceptionHandler;
import secretchat.config.GatewayConfig;
import secretchat.service.SessionManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow;
import java.util.function.DoubleConsumer;


public class ApiClient {
    private static final ApiClient INSTANCE = new ApiClient();

    private final HttpClient client;
    private final ObjectMapper mapper;

    private ApiClient() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .sslContext(secretchat.util.SslUtils.getSslContext())
                .build();
        this.mapper = new ObjectMapper();
    }

    public static ApiClient getInstance() {
        return INSTANCE;
    }

    public String refreshSession() throws Exception {
        return refreshAccessToken();
    }

    private synchronized String refreshAccessToken() throws Exception {
        String refreshToken = SessionManager.getInstance().getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RuntimeException("No refresh token available");
        }

        String url = GatewayConfig.getInstance().getGatewayUrl() + "/api/users/auth/refresh";
        String jsonBody = "{\"refreshToken\":\"" + refreshToken + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            SessionManager.getInstance().clear();
            throw new RuntimeException("Refresh token failed: " + response.statusCode());
        }

        JsonNode jsonNode = mapper.readTree(response.body());
        String newAccessToken = jsonNode.get("accessToken").asText();
        String newRefreshToken = jsonNode.has("refreshToken") && !jsonNode.get("refreshToken").isNull() 
            ? jsonNode.get("refreshToken").asText() : null;

        SessionManager.getInstance().setAccessToken(newAccessToken);
        if (newRefreshToken != null && !newRefreshToken.isBlank()) {
            SessionManager.getInstance().setRefreshToken(newRefreshToken);
        }

        return newAccessToken;
    }

    private HttpResponse<String> sendWithRetry(HttpRequest.Builder builder, String accessToken) throws Exception {
        return sendWithRetry(builder, accessToken, Duration.ofSeconds(30));
    }

    private HttpResponse<String> sendWithRetry(
            HttpRequest.Builder builder, String accessToken, Duration timeout) throws Exception {
        builder.timeout(timeout);
        if (accessToken != null && !accessToken.isBlank()) {
            builder.setHeader("Authorization", "Bearer " + accessToken);
        }
        
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401 && accessToken != null && !accessToken.isBlank()) {
            try {
                String newAccessToken = refreshAccessToken();
                builder.setHeader("Authorization", "Bearer " + newAccessToken);
                response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                // Return original 401 response if refresh fails
            }
        }
        
        return response;
    }

    public <T, R> R post(String path, T requestBody, Class<R> responseClass) throws Exception {
        return post(path, requestBody, null, responseClass);
    }

    public <T, R> R post(String path, T requestBody, String accessToken, Class<R> responseClass) throws Exception {
        String url = GatewayConfig.getInstance().getGatewayUrl() + path;
        return postAbsolute(url, requestBody, accessToken, responseClass);
    }

    public <T, R> R postAbsolute(
            String url, T requestBody, String accessToken, Class<R> responseClass) throws Exception {
        return postAbsolute(url, requestBody, accessToken, responseClass, Duration.ofSeconds(30));
    }

    public <T, R> R postAbsolute(
            String url,
            T requestBody,
            String accessToken,
            Class<R> responseClass,
            Duration timeout) throws Exception {
        // BƯỚC 4: THỰC HIỆN GỬI HTTP POST REQUEST QUA API GATEWAY
        String jsonBody = mapper.writeValueAsString(requestBody);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

        HttpResponse<String> response = sendWithRetry(builder, accessToken, timeout);

        if (response.statusCode() >= 400) {
            throw GlobalExceptionHandler.handle(
                    new RuntimeException(
                            "API error: Status code " + response.statusCode() + ", body: " + response.body()));
        }

        return mapper.readValue(response.body(), responseClass);
    }

    public <T, R> R put(String path, T requestBody, String accessToken, Class<R> responseClass) throws Exception {
        String jsonBody = mapper.writeValueAsString(requestBody);
        String url = GatewayConfig.getInstance().getGatewayUrl() + path;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody));

        HttpResponse<String> response = sendWithRetry(builder, accessToken);

        if (response.statusCode() >= 400) {
            throw GlobalExceptionHandler.handle(
                    new RuntimeException(
                            "API error: Status code " + response.statusCode() + ", body: " + response.body()));
        }

        return mapper.readValue(response.body(), responseClass);
    }

    public <R> R put(String path, String accessToken, Class<R> responseClass) throws Exception {
        String url = GatewayConfig.getInstance().getGatewayUrl() + path;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .PUT(HttpRequest.BodyPublishers.noBody());

        HttpResponse<String> response = sendWithRetry(builder, accessToken);

        if (response.statusCode() >= 400) {
            throw GlobalExceptionHandler.handle(
                    new RuntimeException(
                            "API error: Status code " + response.statusCode() + ", body: " + response.body()));
        }

        return mapper.readValue(response.body(), responseClass);
    }

    public <R> R get(String path, Class<R> responseClass) throws Exception {
        return get(path, null, responseClass);
    }

    public <R> R get(String path, String accessToken, Class<R> responseClass) throws Exception {
        String url = GatewayConfig.getInstance().getGatewayUrl() + path;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET();

        HttpResponse<String> response = sendWithRetry(builder, accessToken);

        if (response.statusCode() >= 400) {
            throw GlobalExceptionHandler.handle(
                    new RuntimeException(
                            "API error: Status code " + response.statusCode() + ", body: " + response.body()));
        }

        return mapper.readValue(response.body(), responseClass);
    }

    public byte[] getBytes(String path, String accessToken) throws Exception {
        String url = GatewayConfig.getInstance().getGatewayUrl() + path;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET();

        if (accessToken != null && !accessToken.isBlank()) {
            builder.setHeader("Authorization", "Bearer " + accessToken);
        }

        HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() == 401 && accessToken != null && !accessToken.isBlank()) {
            try {
                String newAccessToken = refreshAccessToken();
                builder.setHeader("Authorization", "Bearer " + newAccessToken);
                response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            } catch (Exception e) {
                // Return original 401 response if refresh fails
            }
        }

        if (response.statusCode() >= 400) {
            throw GlobalExceptionHandler.handle(
                    new RuntimeException(
                            "API error: Status code " + response.statusCode()));
        }

        return response.body();
    }

    public String uploadFile(String path, java.io.File file, String accessToken) throws Exception {
        return uploadFile(path, file, accessToken, ignored -> {});
    }

    public String uploadFile(
            String path, java.io.File file, String accessToken, DoubleConsumer progressListener) throws Exception {
        String url = GatewayConfig.getInstance().getGatewayUrl() + path;

        String boundary = "---" + java.util.UUID.randomUUID();
        String safeFileName = file.getName().replace("\"", "_").replace("\r", "").replace("\n", "");
        String header = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"" + safeFileName + "\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";

        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] footerBytes = footer.getBytes(StandardCharsets.UTF_8);
        long contentLength = headerBytes.length + file.length() + footerBytes.length;
        HttpRequest.BodyPublisher multipartBody = HttpRequest.BodyPublishers.concat(
                HttpRequest.BodyPublishers.ofByteArray(headerBytes),
                HttpRequest.BodyPublishers.ofFile(file.toPath()),
                HttpRequest.BodyPublishers.ofByteArray(footerBytes));

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(new ProgressBodyPublisher(multipartBody, contentLength, progressListener));

        HttpResponse<String> response = sendWithRetry(builder, accessToken);

        if (response.statusCode() >= 400) {
            throw new RuntimeException("Upload failed: " + response.statusCode());
        }
        
        return response.body(); 
    }

    private static final class ProgressBodyPublisher implements HttpRequest.BodyPublisher {
        private final HttpRequest.BodyPublisher delegate;
        private final long contentLength;
        private final DoubleConsumer progressListener;

        private ProgressBodyPublisher(
                HttpRequest.BodyPublisher delegate,
                long contentLength,
                DoubleConsumer progressListener) {
            this.delegate = delegate;
            this.contentLength = contentLength;
            this.progressListener = progressListener;
        }

        @Override
        public long contentLength() {
            return contentLength;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            progressListener.accept(0);
            delegate.subscribe(new Flow.Subscriber<>() {
                private long transferred;

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscriber.onSubscribe(subscription);
                }

                @Override
                public void onNext(ByteBuffer item) {
                    transferred += item.remaining();
                    subscriber.onNext(item);
                    progressListener.accept(contentLength == 0
                            ? 1
                            : Math.min(1, (double) transferred / contentLength));
                }

                @Override
                public void onError(Throwable error) {
                    subscriber.onError(error);
                }

                @Override
                public void onComplete() {
                    progressListener.accept(1);
                    subscriber.onComplete();
                }
            });
        }
    }

    public void delete(String path, String accessToken) throws Exception {
        String url = GatewayConfig.getInstance().getGatewayUrl() + path;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .DELETE();

        HttpResponse<String> response = sendWithRetry(builder, accessToken);

        if (response.statusCode() >= 400) {
            throw GlobalExceptionHandler.handle(
                    new RuntimeException(
                            "API error: Status code " + response.statusCode() + ", body: " + response.body()));
        }
    }
}
