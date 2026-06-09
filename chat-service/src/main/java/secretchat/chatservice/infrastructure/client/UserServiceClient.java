package secretchat.chatservice.infrastructure.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import secretchat.chatservice.infrastructure.client.dto.UserServiceUserResponse;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceClient {

    private final RestTemplate restTemplate;

    @Value("${app.user-service.url:http://localhost:8082}")
    private String userServiceUrl;

    public Optional<UserServiceUserResponse> getUserByUsername(String username) {
        String url = userServiceUrl + "/api/users/username/" + username;
        return executeGet(url);
    }

    public Optional<UserServiceUserResponse> getUserByKeycloakId(String keycloakId) {
        String url = userServiceUrl + "/api/users/" + keycloakId;
        return executeGet(url);
    }

    private Optional<UserServiceUserResponse> executeGet(String url) {
        HttpHeaders headers = new HttpHeaders();
        String token = getBearerToken();
        
        if (token != null) {
            headers.set("Authorization", token);
            log.debug("Using token from request context for user-service call");
        } else {
            log.debug("No bearer token found in request context for user-service call to {}", url);
        }

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<UserServiceUserResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    UserServiceUserResponse.class
            );
            return Optional.ofNullable(response.getBody());
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("User not found at url: {}", url);
            return Optional.empty();
        } catch (HttpClientErrorException.Unauthorized e) {
            log.error("Unauthorized (401) calling user-service at url: {}. Token: {}. Headers: {}", 
                    url, token, headers, e);
            throw new RuntimeException("Unauthorized to access user-service: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error calling user-service at url: {}", url, e);
            throw new RuntimeException("Error communicating with user-service: " + e.getMessage(), e);
        }
    }

    private String getBearerToken() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            log.info("attributes = {}", attributes);

            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();

                log.info("Authorization header = {}", request.getHeader("Authorization"));

                String authHeader = request.getHeader("Authorization");

                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    return authHeader;
                }
            }
        } catch (Exception e) {
            log.error("Error extracting token", e);
        }

        return null;
    }
}
