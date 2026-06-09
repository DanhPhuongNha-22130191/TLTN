package secretchat.userservice.domain.model;

import secretchat.userservice.domain.enums.UserStatus;
import secretchat.userservice.domain.valueobject.Email;
import secretchat.userservice.domain.valueobject.FullName;
import secretchat.userservice.domain.valueobject.KeycloakUserId;
import secretchat.userservice.domain.valueobject.PhoneNumber;

import java.time.LocalDateTime;

public class User {

    private final KeycloakUserId keycloakUserId;
    private final String         username;
    private final Email          email;
    private final FullName       fullName;
    private final String         avatar;
    private final PhoneNumber    phoneNumber;
    private final UserStatus     status;
    private final LocalDateTime  createdAt;

    private User(Builder builder) {
        this.keycloakUserId = builder.keycloakUserId;
        this.username       = builder.username;
        this.email          = builder.email;
        this.fullName       = builder.fullName;
        this.avatar         = builder.avatar;
        this.phoneNumber    = builder.phoneNumber;
        this.status         = builder.status;
        this.createdAt      = builder.createdAt;
        validate();
    }

    private void validate() {
        if (keycloakUserId == null) {
            throw new IllegalStateException("Keycloak user id is required");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("Username is required");
        }
        if (email == null) {
            throw new IllegalStateException("Email is required");
        }
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public String getDisplayName() {
        return fullName != null ? fullName.getDisplayName() : username;
    }

    public KeycloakUserId getKeycloakUserId() { return keycloakUserId; }
    public String         getUsername()        { return username; }
    public Email          getEmail()           { return email; }
    public FullName       getFullName()        { return fullName; }
    public String         getAvatar()          { return avatar; }
    public PhoneNumber    getPhoneNumber()     { return phoneNumber; }
    public UserStatus     getStatus()          { return status; }
    public LocalDateTime  getCreatedAt()       { return createdAt; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private KeycloakUserId keycloakUserId;
        private String         username;
        private Email          email;
        private FullName       fullName;
        private String         avatar;
        private PhoneNumber    phoneNumber;
        private UserStatus     status    = UserStatus.ACTIVE;
        private LocalDateTime  createdAt;

        public Builder keycloakUserId(KeycloakUserId keycloakUserId) { this.keycloakUserId = keycloakUserId; return this; }
        public Builder username(String username)                      { this.username = username;               return this; }
        public Builder email(Email email)                             { this.email = email;                     return this; }
        public Builder fullName(FullName fullName)                    { this.fullName = fullName;               return this; }
        public Builder avatar(String avatar)                          { this.avatar = avatar;                   return this; }
        public Builder phoneNumber(PhoneNumber phoneNumber)           { this.phoneNumber = phoneNumber;         return this; }
        public Builder status(UserStatus status)                      { this.status = status;                   return this; }
        public Builder createdAt(LocalDateTime createdAt)             { this.createdAt = createdAt;             return this; }

        public User build() {
            return new User(this);
        }
    }
}
