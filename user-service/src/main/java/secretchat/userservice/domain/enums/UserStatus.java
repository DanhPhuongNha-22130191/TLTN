package secretchat.userservice.domain.enums;

public enum UserStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    DELETED;

    public static UserStatus fromValue(String value) {
        for (UserStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown UserStatus: " + value);
    }
}
