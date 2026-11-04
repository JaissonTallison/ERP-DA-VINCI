package br.com.davinci.erp.organization.shared.security;

import br.com.davinci.erp.organization.shared.tenancy.TenantClaims;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Tokens do realm davinci (emissor e audiência {@code organization} validados pela configuração). O token precisa de
 * exatamente um tenant (claim {@code organization}); papéis vêm de {@code realm_access.roles}.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/v1/**")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(
                        oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(new TenantTokenConverter())))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // API sem cookies nem sessão: CSRF não se aplica.
                .csrf(csrf -> csrf.disable())
                .build();
    }

    static final class TenantTokenConverter implements Converter<Jwt, AbstractAuthenticationToken> {

        @Override
        public AbstractAuthenticationToken convert(Jwt jwt) {
            if (TenantClaims.tenantOf(jwt.getClaims().get("organization")).isEmpty()) {
                throw new OAuth2AuthenticationException(new OAuth2Error(
                        OAuth2ErrorCodes.INVALID_TOKEN, "Token sem exatamente um tenant (claim organization)", null));
            }
            return new JwtAuthenticationToken(jwt, roles(jwt), jwt.getSubject());
        }

        private static Collection<GrantedAuthority> roles(Jwt jwt) {
            if (jwt.getClaims().get("realm_access") instanceof Map<?, ?> access
                    && access.get("roles") instanceof List<?> roles) {
                return roles.stream()
                        .filter(String.class::isInstance)
                        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();
            }
            return List.of();
        }
    }
}
