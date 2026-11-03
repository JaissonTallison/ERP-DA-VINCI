package br.com.davinci.erp.tenantregistry.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * API protegida por tokens OAuth 2.0 do IdP regional (ADR-0009). Escopos: {@code tenant-registry.read} e
 * {@code tenant-registry.write}; o emissor e a audiência são validados pela configuração do resource server.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    private static final String READ = "SCOPE_tenant-registry.read";
    private static final String WRITE = "SCOPE_tenant-registry.write";

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/actuator/sbom/**")
                        .hasAnyAuthority(READ, WRITE)
                        .requestMatchers(HttpMethod.GET, "/v1/**")
                        .hasAnyAuthority(READ, WRITE)
                        .requestMatchers("/v1/**")
                        .hasAuthority(WRITE)
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // API sem cookies nem sessão: CSRF não se aplica.
                .csrf(csrf -> csrf.disable())
                .build();
    }
}
