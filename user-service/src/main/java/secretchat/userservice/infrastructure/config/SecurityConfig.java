package secretchat.userservice.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/users/auth/**",
                                "/auth/**",
                                "/actuator/health",
                                "/error"
                        ).permitAll()
                        .requestMatchers("/api/users/username/**").permitAll()
                        .requestMatchers("/api/users/**").hasRole("USER")

                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                )
                .build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopeConverter =
                new JwtGrantedAuthoritiesConverter();

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities =
                    new ArrayList<>();

            Collection<GrantedAuthority> scopeAuthorities =
                    scopeConverter.convert(jwt);

            if (scopeAuthorities != null) {
                authorities.addAll(scopeAuthorities);
            }

            Map<String, Object> realmAccess =
                    jwt.getClaim("realm_access");

            if (realmAccess != null) {
                Object rolesObject =
                        realmAccess.get("roles");

                if (rolesObject instanceof Collection<?> roles) {
                    roles.forEach(role -> authorities.add(
                            new SimpleGrantedAuthority(
                                    "ROLE_" + role.toString()
                            )
                    ));
                }
            }

            return authorities;
        });

        return converter;
    }
}