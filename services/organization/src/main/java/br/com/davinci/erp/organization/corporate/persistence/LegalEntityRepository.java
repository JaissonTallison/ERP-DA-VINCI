package br.com.davinci.erp.organization.corporate.persistence;

import br.com.davinci.erp.organization.corporate.domain.GroupAssignment;
import br.com.davinci.erp.organization.corporate.domain.LegalEntity;
import br.com.davinci.erp.organization.corporate.domain.LegalEntityStatus;
import br.com.davinci.erp.organization.corporate.domain.TaxIdentifierRecord;
import br.com.davinci.erp.organization.shared.domain.BusinessCode;
import br.com.davinci.erp.organization.shared.domain.CountryCode;
import br.com.davinci.erp.organization.shared.domain.EffectivePeriod;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Entidades jurídicas, pertença a grupos e identificadores fiscais do tenant da transação. */
@Repository
public class LegalEntityRepository {

    private static final String COLUMNS = """
            id, tenant_id, code, legal_name, trade_name, country, subdivision, legal_form, functional_currency, timezone,
            default_locale, fiscal_year_start_month, registered_address, incorporated_on, closed_on, status, created_at,
            updated_at, version""";

    private final JdbcClient jdbc;
    private final AddressJson addresses;
    private final TaxIdentifierTable taxes;

    public LegalEntityRepository(JdbcClient jdbc, AddressJson addresses) {
        this.jdbc = jdbc;
        this.addresses = addresses;
        this.taxes = new TaxIdentifierTable(jdbc, "tax_identifier", "legal_entity_id");
    }

    public void insert(LegalEntity entity) {
        jdbc.sql("INSERT INTO legal_entity (" + COLUMNS + ")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?)")
                .params(
                        entity.id(),
                        entity.tenantId().value(),
                        entity.code().value(),
                        entity.legalName(),
                        entity.tradeName(),
                        entity.country().value(),
                        entity.subdivision(),
                        entity.legalForm(),
                        entity.functionalCurrency().getCurrencyCode(),
                        entity.timezone().getId(),
                        entity.defaultLocale().toLanguageTag(),
                        entity.fiscalYearStartMonth(),
                        addresses.write(entity.registeredAddress()),
                        entity.incorporatedOn(),
                        entity.closedOn(),
                        entity.status().name(),
                        Rows.timestamp(entity.createdAt()),
                        Rows.timestamp(entity.updatedAt()),
                        entity.version())
                .update();
    }

    public void update(LegalEntity entity) {
        jdbc.sql("""
                        UPDATE legal_entity SET legal_name = ?, trade_name = ?, legal_form = ?, functional_currency = ?,
                               timezone = ?, default_locale = ?, fiscal_year_start_month = ?,
                               registered_address = CAST(? AS jsonb), incorporated_on = ?, closed_on = ?, status = ?,
                               updated_at = ?, version = ?
                        WHERE id = ?
                        """)
                .params(
                        entity.legalName(),
                        entity.tradeName(),
                        entity.legalForm(),
                        entity.functionalCurrency().getCurrencyCode(),
                        entity.timezone().getId(),
                        entity.defaultLocale().toLanguageTag(),
                        entity.fiscalYearStartMonth(),
                        addresses.write(entity.registeredAddress()),
                        entity.incorporatedOn(),
                        entity.closedOn(),
                        entity.status().name(),
                        Rows.timestamp(entity.updatedAt()),
                        entity.version(),
                        entity.id())
                .update();
    }

    /** Marca a entidade como alterada (nova versão) sem mexer nos atributos: mudanças de grupo e de identificador. */
    public void touch(UUID id, long version, Instant now) {
        jdbc.sql("UPDATE legal_entity SET version = ?, updated_at = ? WHERE id = ?")
                .params(version, Rows.timestamp(now), id)
                .update();
    }

    public Optional<LegalEntity> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM legal_entity WHERE id = ?")
                .param(id)
                .query(this::map)
                .optional();
    }

    public Optional<LegalEntity> findForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM legal_entity WHERE id = ? FOR UPDATE")
                .param(id)
                .query(this::map)
                .optional();
    }

    public boolean existsByCode(BusinessCode code) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM legal_entity WHERE code = ?)")
                .param(code.value())
                .query(Boolean.class)
                .single();
    }

    public record Filter(
            @Nullable String country,
            @Nullable LegalEntityStatus status,
            @Nullable UUID economicGroupId,
            LocalDate asOf,
            @Nullable String text) {}

    public List<LegalEntity> page(Filter filter, @Nullable String afterCode, int limit) {
        return jdbc.sql("""
                        SELECT %s FROM legal_entity e
                        WHERE (CAST(:country AS text) IS NULL OR e.country = :country)
                          AND (CAST(:status AS text) IS NULL OR e.status = :status)
                          AND (CAST(:group AS uuid) IS NULL OR EXISTS (
                                SELECT 1 FROM legal_entity_group_assignment a
                                 WHERE a.legal_entity_id = e.id AND a.economic_group_id = :group
                                   AND a.valid_from <= :asOf AND (a.valid_to IS NULL OR a.valid_to > :asOf)))
                          AND (CAST(:text AS text) IS NULL OR e.code ILIKE :text OR e.legal_name ILIKE :text)
                          AND (CAST(:after AS text) IS NULL OR e.code > :after)
                        ORDER BY e.code LIMIT :limit
                        """.formatted(COLUMNS.replace("\n", " ")))
                .param("country", filter.country())
                .param(
                        "status",
                        filter.status() == null ? null : filter.status().name())
                .param("group", filter.economicGroupId())
                .param("asOf", filter.asOf())
                .param(
                        "text",
                        filter.text() == null ? null : "%" + filter.text().replace("%", "\\%") + "%")
                .param("after", afterCode)
                .param("limit", limit)
                .query(this::map)
                .list();
    }

    public Map<UUID, List<GroupAssignment>> assignments(Collection<UUID> entityIds) {
        Map<UUID, List<GroupAssignment>> result = new LinkedHashMap<>();
        entityIds.forEach(id -> result.put(id, new ArrayList<>()));
        if (entityIds.isEmpty()) {
            return result;
        }
        jdbc.sql("""
                        SELECT legal_entity_id, economic_group_id, valid_from, valid_to
                        FROM legal_entity_group_assignment WHERE legal_entity_id = ANY(?) ORDER BY valid_from
                        """)
                .param(entityIds.toArray(UUID[]::new))
                .query((rs, row) -> {
                    result.get(rs.getObject("legal_entity_id", UUID.class))
                            .add(new GroupAssignment(
                                    rs.getObject("economic_group_id", UUID.class),
                                    new EffectivePeriod(
                                            rs.getObject("valid_from", LocalDate.class), Rows.date(rs, "valid_to"))));
                    return row;
                })
                .list();
        return result;
    }

    /**
     * Grava a mudança de histórico de grupos alterando só o que mudou: encerra vigências ({@code UPDATE}), abre novas
     * ({@code INSERT}) e só apaga o vínculo substituído no mesmo dia em que começava (correção). O EXCLUDE do banco
     * garante que não há sobreposição.
     */
    public void saveAssignments(
            TenantId tenant, UUID entityId, List<GroupAssignment> before, List<GroupAssignment> after) {
        for (GroupAssignment old : before) {
            boolean kept = after.stream().anyMatch(a -> sameStart(a, old));
            if (!kept) {
                jdbc.sql(
                                "DELETE FROM legal_entity_group_assignment WHERE legal_entity_id = ? AND economic_group_id = ? AND valid_from = ?")
                        .params(entityId, old.economicGroupId(), old.period().validFrom())
                        .update();
            }
        }
        for (GroupAssignment a : after) {
            var previous = before.stream().filter(b -> sameStart(b, a)).findFirst();
            if (previous.isEmpty()) {
                jdbc.sql(
                                "INSERT INTO legal_entity_group_assignment (tenant_id, legal_entity_id, economic_group_id, valid_from, valid_to)"
                                        + " VALUES (?, ?, ?, ?, ?)")
                        .params(
                                tenant.value(),
                                entityId,
                                a.economicGroupId(),
                                a.period().validFrom(),
                                a.period().validTo())
                        .update();
            } else if (!previous.get().equals(a)) {
                jdbc.sql("UPDATE legal_entity_group_assignment SET valid_to = ?"
                                + " WHERE legal_entity_id = ? AND economic_group_id = ? AND valid_from = ?")
                        .params(
                                a.period().validTo(),
                                entityId,
                                a.economicGroupId(),
                                a.period().validFrom())
                        .update();
            }
        }
    }

    private static boolean sameStart(GroupAssignment a, GroupAssignment b) {
        return a.economicGroupId().equals(b.economicGroupId())
                && a.period().validFrom().equals(b.period().validFrom());
    }

    public Map<UUID, List<TaxIdentifierRecord>> taxIdentifiers(Collection<UUID> entityIds) {
        return taxes.load(entityIds);
    }

    public void insertTaxIdentifier(TenantId tenant, UUID entityId, TaxIdentifierRecord record) {
        taxes.insert(tenant, entityId, record);
    }

    public void endTaxIdentifier(UUID taxIdentifierId, LocalDate on) {
        taxes.end(taxIdentifierId, on);
    }

    /** Estabelecimentos ou unidades não encerrados ligados à entidade (impedem o encerramento dela). */
    public boolean hasOpenDependents(UUID entityId) {
        return jdbc.sql(
                        "SELECT EXISTS (SELECT 1 FROM establishment WHERE legal_entity_id = ? AND status <> 'CLOSED')"
                                + " OR EXISTS (SELECT 1 FROM organizational_unit WHERE legal_entity_id = ? AND status <> 'CLOSED')")
                .params(entityId, entityId)
                .query(Boolean.class)
                .single();
    }

    private LegalEntity map(ResultSet rs, int row) throws SQLException {
        return new LegalEntity(
                rs.getObject("id", UUID.class),
                new TenantId(rs.getObject("tenant_id", UUID.class)),
                new BusinessCode(rs.getString("code")),
                rs.getString("legal_name"),
                rs.getString("trade_name"),
                new CountryCode(rs.getString("country")),
                rs.getString("subdivision"),
                rs.getString("legal_form"),
                Currency.getInstance(rs.getString("functional_currency")),
                ZoneId.of(rs.getString("timezone")),
                Locale.forLanguageTag(rs.getString("default_locale")),
                rs.getInt("fiscal_year_start_month"),
                addresses.read(rs.getString("registered_address")),
                Rows.date(rs, "incorporated_on"),
                Rows.date(rs, "closed_on"),
                LegalEntityStatus.valueOf(rs.getString("status")),
                Rows.instant(rs, "created_at"),
                Rows.instant(rs, "updated_at"),
                rs.getLong("version"));
    }
}
