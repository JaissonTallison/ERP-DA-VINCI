package br.com.davinci.erp.organization.structure.persistence;

import br.com.davinci.erp.organization.shared.domain.BusinessCode;
import br.com.davinci.erp.organization.shared.domain.EffectivePeriod;
import br.com.davinci.erp.organization.structure.domain.Hierarchy;
import br.com.davinci.erp.organization.structure.domain.OrganizationalStructure;
import br.com.davinci.erp.organization.structure.domain.StructureLink;
import br.com.davinci.erp.organization.structure.domain.StructurePurpose;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Estruturas, vínculos com vigência e leituras de árvore por data (CTE recursiva, ADR-0019). */
@Repository
public class StructureRepository {

    private static final String COLUMNS = "id, tenant_id, code, name, purpose, created_at, updated_at, version";

    /** Colunas de uma unidade na árvore; {@code l} = vínculo, {@code u} = unidade, {@code t} = tipo. */
    private static final String UNIT = """
            l.unit_id, u.code, u.name ->> 'text' AS name_text, (u.name -> 'translations')::text AS name_translations,
            t.code AS type_code, u.status, l.parent_unit_id""";

    private static final String HAS_CHILDREN = """
            EXISTS (SELECT 1 FROM structure_link c WHERE c.structure_id = l.structure_id AND c.parent_unit_id = l.unit_id
                      AND c.valid_from <= :asOf AND (c.valid_to IS NULL OR c.valid_to > :asOf))""";

    private static final String NODE = UNIT + ", " + HAS_CHILDREN + " AS has_children";

    private final JdbcClient jdbc;
    private final LocalizedTextJson texts;

    public StructureRepository(JdbcClient jdbc, LocalizedTextJson texts) {
        this.jdbc = jdbc;
        this.texts = texts;
    }

    public void insert(OrganizationalStructure s) {
        jdbc.sql("INSERT INTO organizational_structure (" + COLUMNS
                        + ") VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?)")
                .params(
                        s.id(),
                        s.tenantId().value(),
                        s.code().value(),
                        texts.write(s.name()),
                        s.purpose().name(),
                        Rows.timestamp(s.createdAt()),
                        Rows.timestamp(s.updatedAt()),
                        s.version())
                .update();
    }

    public Optional<OrganizationalStructure> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM organizational_structure WHERE id = ?")
                .param(id)
                .query(this::map)
                .optional();
    }

    public boolean existsByCode(BusinessCode code) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM organizational_structure WHERE code = ?)")
                .param(code.value())
                .query(Boolean.class)
                .single();
    }

    public List<OrganizationalStructure> list() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM organizational_structure ORDER BY code")
                .query(this::map)
                .list();
    }

    /** Serializa as mudanças de uma estrutura até o fim da transação (a checagem de ciclo não sofre corrida). */
    public void lock(UUID structureId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")
                .param(structureId.toString())
                .query(Object.class)
                .list();
    }

    /** Vínculos que valem em {@code on} ou depois (os anteriores não mudam e não afetam a checagem). */
    public List<StructureLink> linksFrom(UUID structureId, LocalDate on) {
        return jdbc.sql("""
                        SELECT unit_id, parent_unit_id, valid_from, valid_to FROM structure_link
                        WHERE structure_id = ? AND (valid_to IS NULL OR valid_to > ?)
                        """)
                .params(structureId, on)
                .query((rs, row) -> new StructureLink(
                        rs.getObject("unit_id", UUID.class),
                        Rows.uuid(rs, "parent_unit_id"),
                        new EffectivePeriod(rs.getObject("valid_from", LocalDate.class), Rows.date(rs, "valid_to"))))
                .list();
    }

    /** Grava só a diferença: encerra vigências, abre novas e apaga apenas vínculos substituídos no mesmo dia. */
    public void saveLinks(TenantId tenant, UUID structureId, List<StructureLink> before, List<StructureLink> after) {
        for (StructureLink old : before) {
            if (after.stream().noneMatch(a -> sameStart(a, old))) {
                jdbc.sql("DELETE FROM structure_link WHERE structure_id = ? AND unit_id = ? AND valid_from = ?")
                        .params(structureId, old.unitId(), old.period().validFrom())
                        .update();
            }
        }
        for (StructureLink link : after) {
            var previous = before.stream().filter(b -> sameStart(b, link)).findFirst();
            if (previous.isEmpty()) {
                jdbc.sql(
                                "INSERT INTO structure_link (tenant_id, structure_id, unit_id, parent_unit_id, valid_from, valid_to)"
                                        + " VALUES (?, ?, ?, ?, ?, ?)")
                        .params(
                                tenant.value(),
                                structureId,
                                link.unitId(),
                                link.parentUnitId(),
                                link.period().validFrom(),
                                link.period().validTo())
                        .update();
            } else if (!previous.get().equals(link)) {
                jdbc.sql(
                                "UPDATE structure_link SET valid_to = ? WHERE structure_id = ? AND unit_id = ? AND valid_from = ?")
                        .params(
                                link.period().validTo(),
                                structureId,
                                link.unitId(),
                                link.period().validFrom())
                        .update();
            }
        }
    }

    private static boolean sameStart(StructureLink a, StructureLink b) {
        return a.unitId().equals(b.unitId())
                && a.period().validFrom().equals(b.period().validFrom())
                && Objects.equals(a.parentUnitId(), b.parentUnitId());
    }

    public List<TreeNode> roots(UUID structureId, LocalDate asOf) {
        return jdbc.sql("""
                        SELECT %s, 0 AS depth FROM structure_link l
                        JOIN organizational_unit u ON u.id = l.unit_id JOIN unit_type t ON t.id = u.unit_type_id
                        WHERE l.structure_id = :structure AND l.parent_unit_id IS NULL
                          AND l.valid_from <= :asOf AND (l.valid_to IS NULL OR l.valid_to > :asOf)
                        ORDER BY u.code
                        """.formatted(NODE))
                .param("structure", structureId)
                .param("asOf", asOf)
                .query(this::node)
                .list();
    }

    public List<TreeNode> children(UUID structureId, UUID parent, LocalDate asOf) {
        return jdbc.sql("""
                        SELECT %s, 1 AS depth FROM structure_link l
                        JOIN organizational_unit u ON u.id = l.unit_id JOIN unit_type t ON t.id = u.unit_type_id
                        WHERE l.structure_id = :structure AND l.parent_unit_id = :parent
                          AND l.valid_from <= :asOf AND (l.valid_to IS NULL OR l.valid_to > :asOf)
                        ORDER BY u.code
                        """.formatted(NODE))
                .param("structure", structureId)
                .param("parent", parent)
                .param("asOf", asOf)
                .query(this::node)
                .list();
    }

    /**
     * Subárvore a partir de {@code root} (inclusive), até {@code maxDepth} níveis e {@code limit} nós. A ordem (nível,
     * código) é decidida só com as colunas estreitas; nome e tipo entram depois, sem ordenar linhas largas (com 20 mil
     * nós, a ordenação larga ia para disco). Quem tem filhos sai da própria árvore; só o último nível consulta o
     * índice.
     */
    public List<TreeNode> subtree(UUID structureId, UUID root, LocalDate asOf, int maxDepth, int limit) {
        return jdbc
                .sql("""
                        WITH RECURSIVE tree AS (
                            SELECT l.unit_id, l.parent_unit_id, l.structure_id, 0 AS depth
                              FROM structure_link l
                             WHERE l.structure_id = :structure AND l.unit_id = :root
                               AND l.valid_from <= :asOf AND (l.valid_to IS NULL OR l.valid_to > :asOf)
                            UNION ALL
                            SELECT l.unit_id, l.parent_unit_id, l.structure_id, tree.depth + 1
                              FROM structure_link l JOIN tree ON l.parent_unit_id = tree.unit_id
                             WHERE l.structure_id = :structure
                               AND l.valid_from <= :asOf AND (l.valid_to IS NULL OR l.valid_to > :asOf)
                               AND tree.depth < :maxDepth
                        ), page AS (
                            SELECT tree.*, row_number() OVER (ORDER BY tree.depth, u.code) AS ord
                              FROM tree JOIN organizational_unit u ON u.id = tree.unit_id
                             ORDER BY tree.depth, u.code
                             LIMIT :limit
                        )
                        SELECT %s, l.depth, l.ord,
                               p.parent_unit_id IS NOT NULL OR (l.depth = :maxDepth AND %s) AS has_children
                          FROM page l
                          JOIN organizational_unit u ON u.id = l.unit_id JOIN unit_type t ON t.id = u.unit_type_id
                          LEFT JOIN (SELECT DISTINCT parent_unit_id FROM tree) p ON p.parent_unit_id = l.unit_id
                        """.formatted(UNIT, HAS_CHILDREN))
                .param("structure", structureId)
                .param("root", root)
                .param("asOf", asOf)
                .param("maxDepth", Math.min(maxDepth, Hierarchy.MAX_DEPTH))
                .param("limit", limit)
                .query((rs, row) -> new Ranked(rs.getLong("ord"), node(rs, row)))
                .list()
                .stream()
                .sorted(Comparator.comparingLong(Ranked::ord))
                .map(Ranked::node)
                .toList();
    }

    /** Caminho da unidade até a raiz (a própria unidade primeiro). */
    public List<TreeNode> ancestors(UUID structureId, UUID unit, LocalDate asOf) {
        return jdbc.sql("""
                        WITH RECURSIVE up AS (
                            SELECT l.unit_id, l.parent_unit_id, l.structure_id, 0 AS depth
                              FROM structure_link l
                             WHERE l.structure_id = :structure AND l.unit_id = :unit
                               AND l.valid_from <= :asOf AND (l.valid_to IS NULL OR l.valid_to > :asOf)
                            UNION ALL
                            SELECT l.unit_id, l.parent_unit_id, l.structure_id, up.depth + 1
                              FROM structure_link l JOIN up ON l.unit_id = up.parent_unit_id
                             WHERE l.structure_id = :structure
                               AND l.valid_from <= :asOf AND (l.valid_to IS NULL OR l.valid_to > :asOf)
                               AND up.depth < %d
                        )
                        SELECT %s, l.depth FROM up l
                        JOIN organizational_unit u ON u.id = l.unit_id JOIN unit_type t ON t.id = u.unit_type_id
                        ORDER BY l.depth
                        """.formatted(Hierarchy.MAX_DEPTH, NODE))
                .param("structure", structureId)
                .param("unit", unit)
                .param("asOf", asOf)
                .query(this::node)
                .list();
    }

    private record Ranked(long ord, TreeNode node) {}

    private TreeNode node(ResultSet rs, int row) throws SQLException {
        return new TreeNode(
                rs.getObject("unit_id", UUID.class),
                rs.getString("code"),
                texts.read(rs.getString("name_text"), rs.getString("name_translations")),
                rs.getString("type_code"),
                rs.getString("status"),
                Rows.uuid(rs, "parent_unit_id"),
                rs.getInt("depth"),
                rs.getBoolean("has_children"));
    }

    private OrganizationalStructure map(ResultSet rs, int row) throws SQLException {
        return new OrganizationalStructure(
                rs.getObject("id", UUID.class),
                new TenantId(rs.getObject("tenant_id", UUID.class)),
                new BusinessCode(rs.getString("code")),
                texts.read(rs.getString("name")),
                StructurePurpose.valueOf(rs.getString("purpose")),
                Rows.instant(rs, "created_at"),
                Rows.instant(rs, "updated_at"),
                rs.getLong("version"));
    }
}
