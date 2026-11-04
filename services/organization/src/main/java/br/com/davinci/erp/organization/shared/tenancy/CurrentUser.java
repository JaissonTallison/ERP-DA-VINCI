package br.com.davinci.erp.organization.shared.tenancy;

import br.com.davinci.erp.platform.kernel.TenantId;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Quem está chamando: tenant, identificador e papéis, sempre a partir do token validado. */
@Component
public class CurrentUser {

    public TenantId tenant() {
        return TenantClaims.tenantOf(token().getToken().getClaims().get("organization"))
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Token sem tenant"));
    }

    /** {@code sub} do token, gravado na auditoria. */
    public String actor() {
        return token().getToken().getSubject();
    }

    public Set<String> authorities() {
        return token().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static JwtAuthenticationToken token() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
            return jwt;
        }
        throw new AuthenticationCredentialsNotFoundException("Requisição sem token");
    }
}
