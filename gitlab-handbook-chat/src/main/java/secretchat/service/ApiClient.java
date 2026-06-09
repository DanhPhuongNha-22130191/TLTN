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
import java.util.concurrent.Flow;
import java.util.function.DoubleConsumer;

public class ApiClient {
    private static final ApiClient INSTANCE = new ApiClient();

    private final HttpClient client;
    private final ObjectMapper mapper;

    private ApiClient() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
    }

    public static ApiClient getInstance() {
        return INSTANCE;
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
        builder.timeout(Duration.ofSeconds(30));
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
        String jsonBody = mapper.writeValueAsString(requestBody);
        String url = GatewayConfig.getInstance().getGatewayUrl() + path;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

        HttpResponse<String> response = sendWithRetry(builder, accessToken);

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
        
        byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
        String boundary = "---" + java.util.UUID.randomUUID().toString();
        
        String header = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";
        
        byte[] headerBytes = header.getBytes("UTF-8");
        byte[] footerBytes = footer.getBytes("UTF-8");
        
        byte[] fullBody = new byte[headerBytes.length + fileBytes.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, fullBody, 0, headerBytes.length);
        System.arraycopy(fileBytes, 0, fullBody, headerBytes.length, fileBytes.length);
        System.arraycopy(footerBytes, 0, fullBody, headerBytes.length + fileBytes.length, footerBytes.length);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(new ProgressBodyPublisher(fullBody, progressListener));

        HttpResponse<String> response = sendWithRetry(builder, accessToken);

        if (response.statusCode() >= 400) {
            throw new RuntimeException("Upload failed: " + response.statusCode());
        }
        
        return response.body(); 
    }

    private static final class ProgressBodyPublisher implements HttpRequest.BodyPublisher {
        private final byte[] body;
        private final DoubleConsumer progressListener;

        private ProgressBodyPublisher(byte[] body, DoubleConsumer progressListener) {
            this.body = body;
            this.progressListener = progressListener;
        }

        @Override
        public long contentLength() {
            return body.length;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            progressListener.accept(0);
            subscriber.onSubscribe(new Flow.Subscription() {
                private static final int CHUNK_SIZE = 64 * 1024;
                private int offset;
                private boolean cancelled;
                private boolean completed;

                @Override
                public synchronized void request(long count) {
                    if (cancelled || completed) return;
                    if (count <= 0) {
                        completed = true;
                        subscriber.onError(new IllegalArgumentException("Non-positive subscription request"));
                        return;
                    }
                    long remainingRequests = count;
                    while (!cancelled && remainingRequests-- > 0 && offset < body.length) {
                        int size = Math.min(CHUNK_SIZE, body.length - offset);
                        ByteBuffer chunk = ByteBuffer.wrap(body, offset, size).slice();
                        offset += size;
                        subscriber.onNext(chunk);
                        progressListener.accept((double) offset / body.length);
                    }
                    if (!cancelled && offset >= body.length && !completed) {
                        completed = true;
                        progressListener.accept(1);
                        subscriber.onComplete();
                    }
                }

                @Override
                public synchronized void cancel() {
                    cancelled = true;
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
