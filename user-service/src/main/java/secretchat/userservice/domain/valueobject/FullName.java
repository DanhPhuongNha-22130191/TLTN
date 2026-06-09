package secretchat.userservice.domain.valueobject;

import java.util.Objects;

public class FullName {
    private final String firstName;
    private final String lastName;
    private final String fullValue;

    public FullName(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name cannot be null or empty");
        }
        this.fullValue = value.trim();
        String[] parts = value.trim().split(" ", 2);
        this.firstName = parts[0];
        this.lastName = parts.length > 1 ? parts[1] : "";
    }

    public FullName(String firstName, String lastName) {
        if ((firstName == null || firstName.trim().isEmpty()) && (lastName == null || lastName.trim().isEmpty())) {
            throw new IllegalArgumentException("First name or last name cannot be null or empty");
        }
        this.firstName = firstName != null ? firstName.trim() : "";
        this.lastName = lastName != null ? lastName.trim() : "";
        this.fullValue = (this.firstName + " " + this.lastName).trim();
    }

    public static FullName of(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return new FullName(value);
    }

    public String getValue() {
        return fullValue;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getDisplayName() {
        return fullValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FullName)) return false;
        FullName fullName = (FullName) o;
        return Objects.equals(fullValue, fullName.fullValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullValue);
    }

    @Override
    public String toString() {
        return fullValue;
    }
}
