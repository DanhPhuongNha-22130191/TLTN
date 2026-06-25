package secretchat.userservice.application.port;

public interface KeycloakUserPort {
    String createUser(String username, String email, String password, String fullName);
    void sendPasswordResetEmail(String keycloakUserId);
    void updateUser(String keycloakUserId, String username, String fullName, String newPassword);
    void deleteUser(String keycloakUserId);
    void assignRole(String keycloakUserId, String role);
    void setEnabled(String keycloakUserId, boolean enabled);
}
