package secretchat.userservice.application.port;

public interface KeycloakUserPort {
    String createUser(String username, String email, String password, String fullName);
    void updateUser(String keycloakUserId, String fullName);
    void deleteUser(String keycloakUserId);
    void assignRole(String keycloakUserId, String role);
}
