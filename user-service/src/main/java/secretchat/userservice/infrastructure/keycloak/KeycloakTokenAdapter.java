package secretchat.userservice.infrastructure.keycloak;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import secretchat.userservice.application.dto.LoginResult;
import secretchat.userservice.application.port.KeycloakTokenPort;
import secretchat.userservice.domain.exception.InvalidCredentialsException;
import secretchat.userservice.domain.exception.InvalidTokenException;
import secretchat.userservice.domain.exception.KeycloakException;

import java.util.Map;

@Component
public class KeycloakTokenAdapter implements KeycloakTokenPort {

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public LoginResult login(String username, String password) {
        String url = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", clientId);
        params.add("grant_type", "password");
        params.add("username", username);
        params.add("password", password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restTemplate.postForObject(
                    url, new HttpEntity<>(params, headers), Map.class
            );

            if (body == null) {
                throw new KeycloakException("Keycloak returned empty response during login");
            }

            return new LoginResult(
                    true,
                    "Login successful",
                    (String) body.get("access_token"),
                    (String) body.get("refresh_token"),
                    (Integer) body.get("expires_in")
            );
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new InvalidCredentialsException(extractKeycloakMessage(e, "Invalid username or password"));
        } catch (HttpClientErrorException.Forbidden e) {
            throw new InvalidCredentialsException("Account is disabled or access denied");
        } catch (HttpClientErrorException e) {
            throw new InvalidCredentialsException(extractKeycloakMessage(e, "Login failed"));
        } catch (RestClientException e) {
            throw new KeycloakException("Cannot connect to authentication server. Please try again later.", e);
        }
    }

    @Override
    public LoginResult refreshToken(String refreshToken) {
        String url = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", clientId);
        params.add("grant_type", "refresh_token");
        params.add("refresh_token", refreshToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restTemplate.postForObject(
                    url, new HttpEntity<>(params, headers), Map.class
            );

            if (body == null) {
                throw new KeycloakException("Keycloak returned empty response during token refresh");
            }

            return new LoginResult(
                    true,
                    "Refresh token successful",
                    (String) body.get("access_token"),
                    (String) body.get("refresh_token"),
                    (Integer) body.get("expires_in")
            );
        } catch (HttpClientErrorException.BadRequest e) {
            throw new InvalidTokenException(extractKeycloakMessage(e, "Refresh token is invalid or already expired"));
        } catch (HttpClientErrorException e) {
            throw new InvalidTokenException(extractKeycloakMessage(e, "Token refresh failed"));
        } catch (RestClientException e) {
            throw new KeycloakException("Cannot connect to authentication server. Please try again later.", e);
        }
    }

    @Override
    public void logout(String refreshToken) {
        String url = serverUrl + "/realms/" + realm + "/protocol/openid-connect/logout";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", clientId);
        params.add("refresh_token", refreshToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            restTemplate.postForEntity(url, new HttpEntity<>(params, headers), Void.class);
        } catch (HttpClientErrorException.BadRequest e) {
            throw new InvalidTokenException(extractKeycloakMessage(e, "Refresh token is invalid or already expired"));
        } catch (HttpClientErrorException e) {
            throw new InvalidTokenException(extractKeycloakMessage(e, "Logout failed"));
        } catch (RestClientException e) {
            throw new KeycloakException("Cannot connect to authentication server. Please try again later.", e);
        }
    }

    /**
     * Parses Keycloak's JSON error body (e.g. {"error":"invalid_grant","error_description":"..."})
     * and returns a clean, user-friendly message.
     */
    private String extractKeycloakMessage(HttpClientErrorException ex, String fallback) {
        try {
            String body = ex.getResponseBodyAsString();
            // Try to extract error_description first, then error
            String desc = extractJsonField(body, "error_description");
            if (desc != null && !desc.isBlank()) return desc;
            String error = extractJsonField(body, "error");
            if (error != null && !error.isBlank()) return error;
        } catch (Exception ignored) {
            // fall through to fallback
        }
        return fallback;
    }

    private String extractJsonField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) return null;
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return null;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }
}
