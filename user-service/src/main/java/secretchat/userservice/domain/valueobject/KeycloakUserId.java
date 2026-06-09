package secretchat.userservice.domain.valueobject;

import java.util.Objects;

public class KeycloakUserId {
    private final String value;

    public KeycloakUserId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Keycloak user id cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static KeycloakUserId of(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return new KeycloakUserId(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KeycloakUserId)) return false;
        KeycloakUserId that = (KeycloakUserId) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
