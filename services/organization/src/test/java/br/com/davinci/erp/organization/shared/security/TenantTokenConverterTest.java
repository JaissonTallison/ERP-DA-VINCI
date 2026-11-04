package br.com.davinci.erp.organization.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

class TenantTokenConverterTest {

    private final SecurityConfig.TenantTokenConverter converter = new SecurityConfig.TenantTokenConverter();

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
    }

    @Test
    void papeisDoRealmViramAutoridades() {
        Jwt jwt = token().claim(
                        "organization",
                        Map.of("acme", Map.of("tenant_id", List.of("0b6f1c1e-6a0e-4d7c-9a51-3a7f4f1b2c3d"))))
                .claim("realm_access", Map.of("roles", List.of("tenant-admin", "offline_access")))
                .build();

        assertThat(converter.convert(jwt).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_tenant-admin", "ROLE_offline_access");
    }

    @Test
    void tokenSemTenantEhRecusadoComoInvalido() {
        assertThatThrownBy(() -> converter.convert(token().claim("scope", "x").build()))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }
}
