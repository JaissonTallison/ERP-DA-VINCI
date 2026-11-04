package br.com.davinci.erp.governance.shared.tenancy;

import br.com.davinci.erp.platform.kernel.TenantId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Define o tenant da transação no PostgreSQL ({@code app.tenant_id}), base do Row-Level Security. É local à
 * transação: ao terminar, a conexão volta ao pool sem tenant. Sem esta chamada, as consultas falham (falha fechada).
 */
@Component
public class TenantSession {

    private final JdbcClient jdbc;

    public TenantSession(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void bind(TenantId tenant) {
        jdbc.sql("SELECT set_config('app.tenant_id', ?, true)")
                .param(tenant.toString())
                .query(String.class)
                .single();
    }
}
