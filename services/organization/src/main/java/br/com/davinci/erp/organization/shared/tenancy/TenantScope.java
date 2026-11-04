package br.com.davinci.erp.organization.shared.tenancy;

import br.com.davinci.erp.platform.kernel.TenantId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Abre o escopo de tenant de um caso de uso: amarra a sessão do banco e, para escrita, exige tenant ativo. */
@Component
public class TenantScope {

    private final CurrentUser user;
    private final TenantSession session;
    private final JdbcClient jdbc;

    public TenantScope(CurrentUser user, TenantSession session, JdbcClient jdbc) {
        this.user = user;
        this.session = session;
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TenantId read() {
        TenantId tenant = user.tenant();
        session.bind(tenant);
        return tenant;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TenantId write() {
        TenantId tenant = read();
        boolean active = jdbc.sql("SELECT status = 'ACTIVE' FROM tenant WHERE tenant_id = ?")
                .param(tenant.value())
                .query(Boolean.class)
                .optional()
                .orElse(false);
        if (!active) {
            throw new TenantNotActiveException();
        }
        return tenant;
    }
}
