package secretchat.userservice.infrastructure.keycloak;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakConfig {

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.admin-cli.client-id}")
    private String adminClientId;

    @Value("${keycloak.admin-cli.username}")
    private String adminUsername;

    @Value("${keycloak.admin-cli.password}")
    private String adminPassword;

    @Bean
    public Keycloak keycloakAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm("master")
                .grantType(OAuth2Constants.PASSWORD)
                .clientId(adminClientId)
                .username(adminUsername)
                .password(adminPassword)
                .build();
    }

    @Bean
    public String keycloakRealm() {
        return realm;
    }
}
