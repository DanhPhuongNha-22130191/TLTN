package secretchat.util;

public class IdUtils {

    public static Long getNumericId(String keycloakUserId) {
        if (keycloakUserId == null) return 0L;
        return (long) Math.abs(keycloakUserId.hashCode());
    }

    public static Long parseLongId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
