package br.com.davinci.erp.identity.sync;

import br.com.davinci.erp.identity.keycloak.KeycloakOrganizations;
import br.com.davinci.erp.identity.keycloak.OrganizationSpec;
import br.com.davinci.erp.identity.tenant.TenantEvent;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantém uma organização no Keycloak por tenant. Cada evento traz o estado completo do tenant, então basta aplicar o
 * mais novo: eventos repetidos ou atrasados (versão menor ou igual à aplicada) são descartados.
 *
 * <p>Só tenants ativos ficam com a organização habilitada: membros de organização desabilitada não autenticam (ou,
 * se não gerenciados, recebem token sem a organização, e o frontend recusa a sessão).
 */
@Service
public class TenantOrganizationSync {

    private static final Logger log = LoggerFactory.getLogger(TenantOrganizationSync.class);

    private final TenantOrganizationRepository projections;
    private final KeycloakOrganizations organizations;
    private final Clock clock;

    TenantOrganizationSync(TenantOrganizationRepository projections, KeycloakOrganizations organizations, Clock clock) {
        this.projections = projections;
        this.organizations = organizations;
        this.clock = clock;
    }

    @Transactional
    public void apply(TenantEvent event) {
        OptionalLong applied = projections.lockAppliedVersion(event.tenantId());
        if (applied.isPresent() && applied.getAsLong() >= event.version()) {
            log.debug(
                    "Evento {} do tenant {} já aplicado (versão {} ≤ {})",
                    event.eventId(),
                    event.tenantId(),
                    event.version(),
                    applied.getAsLong());
            return;
        }
        String organizationId = organizations.upsert(desiredOrganization(event));
        projections.save(event, organizationId, clock.instant());
        log.info(
                "Tenant {} ({}) → organização {} {} (versão {})",
                event.slug(),
                event.status(),
                organizationId,
                event.status() == TenantEvent.TenantStatus.ACTIVE ? "habilitada" : "desabilitada",
                event.version());
    }

    static OrganizationSpec desiredOrganization(TenantEvent event) {
        return new OrganizationSpec(
                event.slug(),
                event.slug(),
                event.displayName(),
                event.status() == TenantEvent.TenantStatus.ACTIVE,
                Map.of(
                        OrganizationSpec.TENANT_ID,
                        List.of(event.tenantId().toString()),
                        "display_name",
                        List.of(event.displayName()),
                        "region",
                        List.of(event.region()),
                        "cell_id",
                        List.of(event.cellId().toString()),
                        "isolation",
                        List.of(event.isolation())));
    }
}
