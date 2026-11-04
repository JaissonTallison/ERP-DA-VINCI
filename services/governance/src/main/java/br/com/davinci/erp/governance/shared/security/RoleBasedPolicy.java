package br.com.davinci.erp.governance.shared.security;

import br.com.davinci.erp.governance.shared.tenancy.CurrentUser;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Autorização provisória até o Governance: {@code tenant-admin} pode tudo no próprio tenant; {@code tenant-user}
 * só lê. O escopo é sempre o tenant do token.
 */
@Component
class RoleBasedPolicy implements PolicyDecisionPoint {

    static final String ADMIN = "ROLE_tenant-admin";
    static final String USER = "ROLE_tenant-user";

    private final CurrentUser user;

    RoleBasedPolicy(CurrentUser user) {
        this.user = user;
    }

    @Override
    public void require(Permission permission) {
        Set<String> roles = user.authorities();
        boolean allowed = roles.contains(ADMIN) || (permission.readOnly() && roles.contains(USER));
        if (!allowed) {
            throw new ForbiddenException(permission);
        }
    }
}
