package secretchat.userservice.infrastructure.keycloak;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import secretchat.userservice.application.port.KeycloakUserPort;
import secretchat.userservice.domain.exception.KeycloakException;
import secretchat.userservice.domain.exception.RoleNotFoundException;
import secretchat.userservice.domain.exception.UserNotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class KeycloakUserAdapter implements KeycloakUserPort {

    private final Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    public KeycloakUserAdapter(Keycloak keycloak) {
        this.keycloak = keycloak;
    }

    @Override
    public String createUser(String username, String email, String password, String fullName) {
        UserRepresentation user = new UserRepresentation();
        user.setEnabled(true);
        user.setUsername(username);
        user.setEmail(email);
        if (fullName != null) {
            String[] parts = fullName.split(" ", 2);
            user.setFirstName(parts[0]);
            user.setLastName(parts.length > 1 ? parts[1] : "");
        }

        RealmResource realmResource = keycloak.realm(realm);

        try (Response response = realmResource.users().create(user)) {
            int status = response.getStatus();
            if (status == 409) {
                throw new KeycloakException("User with username '" + username + "' or email '" + email + "' already exists in Keycloak");
            }
            if (status < 200 || status >= 300) {
                String body = response.readEntity(String.class);
                throw new KeycloakException("Failed to create user in Keycloak (HTTP " + status + "): " + body);
            }

            String userId = CreatedResponseUtil.getCreatedId(response);

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false);

            try {
                realmResource.users().get(userId).resetPassword(credential);
            } catch (Exception e) {
                throw new KeycloakException("User created but failed to set password: " + e.getMessage(), e);
            }

            return userId;
        } catch (KeycloakException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakException("Unexpected error creating user in Keycloak: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateUser(String keycloakUserId, String username, String fullName, String newPassword) {
        try {
            UserRepresentation user = keycloak.realm(realm).users().get(keycloakUserId).toRepresentation();
            if (username != null && !username.isBlank()) user.setUsername(username);
            if (fullName != null) {
                String normalized = fullName.trim();
                if (normalized.isEmpty()) {
                    user.setFirstName("");
                    user.setLastName("");
                } else {
                    String[] parts = normalized.split(" ", 2);
                    user.setFirstName(parts[0]);
                    user.setLastName(parts.length > 1 ? parts[1] : "");
                }
            }
            keycloak.realm(realm).users().get(keycloakUserId).update(user);
            if (newPassword != null && !newPassword.isBlank()) {
                CredentialRepresentation credential = new CredentialRepresentation();
                credential.setType(CredentialRepresentation.PASSWORD);
                credential.setValue(newPassword);
                credential.setTemporary(false);
                keycloak.realm(realm).users().get(keycloakUserId).resetPassword(credential);
            }
        } catch (NotFoundException e) {
            throw new UserNotFoundException("User not found in Keycloak: " + keycloakUserId);
        } catch (Exception e) {
            throw new KeycloakException("Failed to update user in Keycloak: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteUser(String keycloakUserId) {
        try {
            keycloak.realm(realm).users().get(keycloakUserId).remove();
        } catch (NotFoundException e) {
            throw new UserNotFoundException("User not found in Keycloak: " + keycloakUserId);
        } catch (Exception e) {
            throw new KeycloakException("Failed to delete user in Keycloak: " + e.getMessage(), e);
        }
    }

    @Override
    public void assignRole(String keycloakUserId, String role) {
        try {
            var usersResource = keycloak.realm(realm).users();
            var rolesResource = usersResource.get(keycloakUserId).roles().realmLevel();

            List<RoleRepresentation> toRemove = rolesResource.listEffective().stream()
                    .filter(r -> r.getName().equals("ADMIN") || r.getName().equals("USER"))
                    .collect(Collectors.toList());
            if (!toRemove.isEmpty()) {
                rolesResource.remove(toRemove);
            }

            RoleRepresentation newRole;
            try {
                newRole = keycloak.realm(realm).roles().get(role).toRepresentation();
            } catch (NotFoundException e) {
                throw new RoleNotFoundException("Role '" + role + "' does not exist in Keycloak");
            }

            rolesResource.add(List.of(newRole));
        } catch (RoleNotFoundException e) {
            throw e;
        } catch (NotFoundException e) {
            throw new UserNotFoundException("User not found in Keycloak: " + keycloakUserId);
        } catch (Exception e) {
            throw new KeycloakException("Failed to assign role '" + role + "' to user: " + e.getMessage(), e);
        }
    }
}
