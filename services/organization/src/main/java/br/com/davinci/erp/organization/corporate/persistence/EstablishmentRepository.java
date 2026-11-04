package br.com.davinci.erp.organization.corporate.persistence;

import br.com.davinci.erp.organization.corporate.domain.Establishment;
import br.com.davinci.erp.organization.corporate.domain.EstablishmentStatus;
import br.com.davinci.erp.organization.corporate.domain.EstablishmentType;
import br.com.davinci.erp.organization.corporate.domain.TaxIdentifierRecord;
import br.com.davinci.erp.organization.shared.domain.BusinessCode;
import br.com.davinci.erp.organization.shared.domain.ConflictException;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Estabelecimentos e seus identificadores fiscais, no tenant da transação. */
@Repository
public class EstablishmentRepository {

    private static final String COLUMNS =
            "id, tenant_id, legal_entity_id, code, name, type, address, timezone, status, opened_on, closed_on, created_at,"
                    + " updated_at, version";

    private final JdbcClient jdbc;
    private final AddressJson addresses;
    private final TaxIdentifierTable taxes;

    public EstablishmentRepository(JdbcClient jdbc, AddressJson addresses) {
        this.jdbc = jdbc;
        this.addresses = addresses;
        this.taxes = new TaxIdentifierTable(jdbc, "establishment_tax_identifier", "establishment_id");
    }

    public void insert(Establishment e) {
        try {
            jdbc.sql("INSERT INTO establishment (" + COLUMNS
                            + ") VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?, ?)")
                    .params(
                            e.id(),
                            e.tenantId().value(),
                            e.legalEntityId(),
                            e.code().value(),
                            e.name(),
                            e.type().name(),
                            addresses.write(e.address()),
                            e.timezone().getId(),
                            e.status().name(),
                            e.openedOn(),
                            e.closedOn(),
                            Rows.timestamp(e.createdAt()),
                            Rows.timestamp(e.updatedAt()),
                            e.version())
                    .update();
        } catch (DataIntegrityViolationException ex) {
            throw translate(ex);
        }
    }

    public void update(Establishment e) {
        try {
            jdbc.sql("""
                            UPDATE establishment SET name = ?, type = ?, address = CAST(? AS jsonb), timezone = ?, status = ?,
                                   closed_on = ?, updated_at = ?, version = ?
                            WHERE id = ?
                            """)
                    .params(
                            e.name(),
                            e.type().name(),
                            addresses.write(e.address()),
                            e.timezone().getId(),
                            e.status().name(),
                            e.closedOn(),
                            Rows.timestamp(e.updatedAt()),
                            e.version(),
                            e.id())
                    .update();
        } catch (DataIntegrityViolationException ex) {
            throw translate(ex);
        }
    }

    /** Nova versão sem mudar atributos (mudança de identificador fiscal). */
    public void touch(UUID id, long version, Instant now) {
        jdbc.sql("UPDATE establishment SET version = ?, updated_at = ? WHERE id = ?")
                .params(version, Rows.timestamp(now), id)
                .update();
    }

    public Optional<Establishment> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM establishment WHERE id = ?")
                .param(id)
                .query(this::map)
                .optional();
    }

    public Optional<Establishment> findForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM establishment WHERE id = ? FOR UPDATE")
                .param(id)
                .query(this::map)
                .optional();
    }

    public boolean existsByCode(UUID legalEntityId, BusinessCode code) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM establishment WHERE legal_entity_id = ? AND code = ?)")
                .params(legalEntityId, code.value())
                .query(Boolean.class)
                .single();
    }

    public record Filter(
            @Nullable UUID legalEntityId,
            @Nullable EstablishmentType type,
            @Nullable EstablishmentStatus status,
            @Nullable String country) {}

    /** Página ordenada por (código, id): o código é único só dentro da entidade. */
    public List<Establishment> page(Filter filter, @Nullable String afterCode, @Nullable UUID afterId, int limit) {
        return jdbc.sql("""
                        SELECT %s FROM establishment
                        WHERE (CAST(:entity AS uuid) IS NULL OR legal_entity_id = :entity)
                          AND (CAST(:type AS text) IS NULL OR type = :type)
                          AND (CAST(:status AS text) IS NULL OR status = :status)
                          AND (CAST(:country AS text) IS NULL OR address ->> 'country' = :country)
                          AND (CAST(:afterCode AS text) IS NULL OR (code, id) > (:afterCode, :afterId))
                        ORDER BY code, id LIMIT :limit
                        """.formatted(COLUMNS))
                .param("entity", filter.legalEntityId())
                .param("type", filter.type() == null ? null : filter.type().name())
                .param(
                        "status",
                        filter.status() == null ? null : filter.status().name())
                .param("country", filter.country())
                .param("afterCode", afterCode)
                .param("afterId", afterId)
                .param("limit", limit)
                .query(this::map)
                .list();
    }

    /** Unidades organizacionais não encerradas localizadas no estabelecimento (impedem o encerramento dele). */
    public boolean hasOpenUnits(UUID establishmentId) {
        return jdbc.sql(
                        "SELECT EXISTS (SELECT 1 FROM organizational_unit WHERE establishment_id = ? AND status <> 'CLOSED')")
                .param(establishmentId)
                .query(Boolean.class)
                .single();
    }

    public Map<UUID, List<TaxIdentifierRecord>> taxIdentifiers(Collection<UUID> ids) {
        return taxes.load(ids);
    }

    public void insertTaxIdentifier(TenantId tenant, UUID establishmentId, TaxIdentifierRecord record) {
        taxes.insert(tenant, establishmentId, record);
    }

    public void endTaxIdentifier(UUID taxIdentifierId, LocalDate on) {
        taxes.end(taxIdentifierId, on);
    }

    private static RuntimeException translate(DataIntegrityViolationException e) {
        String message = String.valueOf(e.getMessage());
        if (message.contains("establishment_one_headquarters")) {
            return new ConflictException(
                    "duplicate-headquarters", "A entidade jurídica já tem uma matriz não encerrada");
        }
        if (message.contains("establishment_code_key")) {
            return new ConflictException("duplicate-code", "Já existe um estabelecimento com este código na entidade");
        }
        return e;
    }

    private Establishment map(ResultSet rs, int row) throws SQLException {
        return new Establishment(
                rs.getObject("id", UUID.class),
                new TenantId(rs.getObject("tenant_id", UUID.class)),
                rs.getObject("legal_entity_id", UUID.class),
                new BusinessCode(rs.getString("code")),
                rs.getString("name"),
                EstablishmentType.valueOf(rs.getString("type")),
                addresses.read(rs.getString("address")),
                ZoneId.of(rs.getString("timezone")),
                EstablishmentStatus.valueOf(rs.getString("status")),
                rs.getObject("opened_on", LocalDate.class),
                Rows.date(rs, "closed_on"),
                Rows.instant(rs, "created_at"),
                Rows.instant(rs, "updated_at"),
                rs.getLong("version"));
    }
}
