package br.com.davinci.erp.organization.costing.persistence;

import br.com.davinci.erp.organization.costing.domain.CostCenter;
import br.com.davinci.erp.organization.costing.domain.CostCenterKind;
import br.com.davinci.erp.organization.costing.domain.CostCenterStatus;
import br.com.davinci.erp.organization.shared.domain.BusinessCode;
import br.com.davinci.erp.organization.shared.domain.EffectivePeriod;
import br.com.davinci.erp.organization.structure.domain.Hierarchy;
import br.com.davinci.erp.organization.structure.domain.StructureLink;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Centros de custo e hierarquia de totalização com vigência. Os vínculos reaproveitam {@link StructureLink} e
 * {@link Hierarchy}: a "estrutura" de um centro de custo é a sua entidade jurídica.
 */
@Repository
public class CostCenterRepository {

    private static final String COLUMNS = """
            id, tenant_id, legal_entity_id, code, name, kind, category, responsible_unit_id, status, valid_from,
            closed_on, created_at, updated_at, version""";

    private final JdbcClient jdbc;

    public CostCenterRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(CostCenter c) {
        jdbc.sql("INSERT INTO cost_center (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(
                        c.id(),
                        c.tenantId().value(),
                        c.legalEntityId(),
                        c.code().value(),
                        c.name(),
                        c.kind().name(),
                        c.category(),
                        c.responsibleUnitId(),
                        c.status().name(),
                        c.validFrom(),
                        c.closedOn(),
                        OffsetDateTime.ofInstant(c.createdAt(), ZoneOffset.UTC),
                        OffsetDateTime.ofInstant(c.updatedAt(), ZoneOffset.UTC),
                        c.version())
                .update();
    }

    public void update(CostCenter c) {
        jdbc.sql("""
                        UPDATE cost_center SET name = ?, category = ?, responsible_unit_id = ?, status = ?, closed_on = ?,
                               updated_at = ?, version = ?
                        WHERE id = ? AND version = ?
                        """)
                .params(
                        c.name(),
                        c.category(),
                        c.responsibleUnitId(),
                        c.status().name(),
                        c.closedOn(),
                        OffsetDateTime.ofInstant(c.updatedAt(), ZoneOffset.UTC),
                        c.version(),
                        c.id(),
                        c.version() - 1)
                .update();
    }

    public Optional<CostCenter> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM cost_center WHERE id = ?")
                .param(id)
                .query(this::map)
                .optional();
    }

    public Optional<CostCenter> findForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM cost_center WHERE id = ? FOR UPDATE")
                .param(id)
                .query(this::map)
                .optional();
    }

    public boolean existsByCode(UUID legalEntityId, BusinessCode code) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM cost_center WHERE legal_entity_id = ? AND code = ?)")
                .params(legalEntityId, code.value())
                .query(Boolean.class)
                .single();
    }

    public List<CostCenter> list(UUID legalEntityId, @Nullable CostCenterStatus status) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM cost_center WHERE legal_entity_id = :entity"
                        + " AND (CAST(:status AS text) IS NULL OR status = :status) ORDER BY code")
                .param("entity", legalEntityId)
                .param("status", status == null ? null : status.name())
                .query(this::map)
                .list();
    }

    /** Trava a hierarquia de uma entidade (mesma ideia da trava por estrutura). */
    public void lockHierarchy(UUID legalEntityId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")
                .param("cost-center:" + legalEntityId)
                .query((rs, row) -> 1)
                .list();
    }

    public List<StructureLink> linksFrom(UUID legalEntityId, LocalDate on) {
        return jdbc.sql("""
                        SELECT cost_center_id, parent_id, valid_from, valid_to FROM cost_center_link
                        WHERE legal_entity_id = ? AND (valid_to IS NULL OR valid_to > ?)
                        """)
                .params(legalEntityId, on)
                .query((rs, row) -> new StructureLink(
                        rs.getObject("cost_center_id", UUID.class),
                        rs.getObject("parent_id", UUID.class),
                        new EffectivePeriod(
                                rs.getObject("valid_from", LocalDate.class),
                                rs.getObject("valid_to", LocalDate.class))))
                .list();
    }

    /** Grava só a diferença, como nos vínculos de estrutura. */
    public void saveLinks(TenantId tenant, UUID legalEntityId, List<StructureLink> before, List<StructureLink> after) {
        for (StructureLink old : before) {
            if (after.stream().noneMatch(a -> sameStart(a, old))) {
                jdbc.sql("DELETE FROM cost_center_link WHERE cost_center_id = ? AND valid_from = ?")
                        .params(old.unitId(), old.period().validFrom())
                        .update();
            }
        }
        for (StructureLink link : after) {
            var previous = before.stream().filter(b -> sameStart(b, link)).findFirst();
            if (previous.isEmpty()) {
                jdbc.sql("""
                                INSERT INTO cost_center_link (tenant_id, legal_entity_id, cost_center_id, parent_id,
                                                              valid_from, valid_to)
                                VALUES (?, ?, ?, ?, ?, ?)
                                """)
                        .params(
                                tenant.value(),
                                legalEntityId,
                                link.unitId(),
                                link.parentUnitId(),
                                link.period().validFrom(),
                                link.period().validTo())
                        .update();
            } else if (!previous.get().equals(link)) {
                jdbc.sql("UPDATE cost_center_link SET valid_to = ? WHERE cost_center_id = ? AND valid_from = ?")
                        .params(
                                link.period().validTo(),
                                link.unitId(),
                                link.period().validFrom())
                        .update();
            }
        }
    }

    public boolean hasChildrenFrom(UUID id, LocalDate on) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM cost_center_link WHERE parent_id = ?"
                        + " AND (valid_to IS NULL OR valid_to > ?))")
                .params(id, on)
                .query(Boolean.class)
                .single();
    }

    /** Hierarquia de uma entidade numa data: centros com o pai vigente (sem vínculo = raiz). */
    public List<Node> tree(UUID legalEntityId, LocalDate asOf) {
        return jdbc.sql("""
                        SELECT c.id, c.code, c.name, c.kind, c.status, l.parent_id
                          FROM cost_center c
                          LEFT JOIN cost_center_link l ON l.cost_center_id = c.id
                               AND l.valid_from <= :asOf AND (l.valid_to IS NULL OR l.valid_to > :asOf)
                         WHERE c.legal_entity_id = :entity AND c.valid_from <= :asOf
                           AND (c.closed_on IS NULL OR c.closed_on > :asOf)
                         ORDER BY c.code
                        """)
                .param("entity", legalEntityId)
                .param("asOf", asOf)
                .query((rs, row) -> new Node(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        CostCenterKind.valueOf(rs.getString("kind")),
                        CostCenterStatus.valueOf(rs.getString("status")),
                        rs.getObject("parent_id", UUID.class)))
                .list();
    }

    public record Node(
            UUID id,
            String code,
            String name,
            CostCenterKind kind,
            CostCenterStatus status,
            @Nullable UUID parentId) {}

    private static boolean sameStart(StructureLink a, StructureLink b) {
        return a.unitId().equals(b.unitId())
                && a.period().validFrom().equals(b.period().validFrom())
                && Objects.equals(a.parentUnitId(), b.parentUnitId());
    }

    private CostCenter map(ResultSet rs, int row) throws SQLException {
        return new CostCenter(
                rs.getObject("id", UUID.class),
                new TenantId(rs.getObject("tenant_id", UUID.class)),
                rs.getObject("legal_entity_id", UUID.class),
                new BusinessCode(rs.getString("code")),
                rs.getString("name"),
                CostCenterKind.valueOf(rs.getString("kind")),
                rs.getString("category"),
                rs.getObject("responsible_unit_id", UUID.class),
                CostCenterStatus.valueOf(rs.getString("status")),
                rs.getObject("valid_from", LocalDate.class),
                rs.getObject("closed_on", LocalDate.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
                rs.getLong("version"));
    }
}
