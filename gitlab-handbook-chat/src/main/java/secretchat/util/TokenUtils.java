package secretchat.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import secretchat.dto.response.UserResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TokenUtils {

    private static final System.Logger LOGGER = System.getLogger(TokenUtils.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String getUsernameFromToken(String jwtToken) {
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length < 2) return null;
            String payload = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Pattern pattern = Pattern.compile("\"preferred_username\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(payload);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Lỗi giải mã username từ token", e);
        }
        return null;
    }

    public static UserResponse getCurrentUserFromToken(String jwtToken) {
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length < 2) return null;
            String payload = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode root = MAPPER.readTree(payload);

            String sub = root.path("sub").asText(null);
            String preferredUsername = root.path("preferred_username").asText(null);
            String fullName = root.path("name").asText(null);
            String email = root.path("email").asText(null);

            if (sub == null || preferredUsername == null) {
                return null;
            }

            UserResponse user = new UserResponse();
            user.setKeycloakUserId(sub);
            user.setUsername(preferredUsername);
            user.setFullName(fullName != null && !fullName.isBlank() ? fullName : preferredUsername);
            user.setEmail(email);
            return user;
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Lỗi giải mã user từ token", e);
            return null;
        }
    }
}
