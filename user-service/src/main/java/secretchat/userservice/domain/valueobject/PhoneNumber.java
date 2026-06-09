package secretchat.userservice.domain.valueobject;

import java.util.Objects;

public class PhoneNumber {
    private final String value;

    public PhoneNumber(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number cannot be null or empty");
        }
        String trimmed = value.trim();
        if (!trimmed.matches("^[+]?\\d{7,15}$")) {
            throw new IllegalArgumentException("Invalid phone number: " + value);
        }
        this.value = trimmed;
    }

    public static PhoneNumber of(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return new PhoneNumber(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PhoneNumber)) return false;
        PhoneNumber that = (PhoneNumber) o;
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
