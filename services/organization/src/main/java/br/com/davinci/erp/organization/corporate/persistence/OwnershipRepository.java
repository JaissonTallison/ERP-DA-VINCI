package br.com.davinci.erp.organization.corporate.persistence;

import br.com.davinci.erp.organization.corporate.domain.ExternalParty;
import br.com.davinci.erp.organization.corporate.domain.OwnershipInterest;
import br.com.davinci.erp.organization.corporate.domain.OwnershipInterest.Holder;
import br.com.davinci.erp.organization.corporate.domain.OwnershipInterest.HolderType;
import br.com.davinci.erp.organization.shared.domain.CountryCode;
import br.com.davinci.erp.organization.shared.domain.EffectivePeriod;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Partes externas e participações societárias com vigência. */
@Repository
public class OwnershipRepository {

    private static final String PARTY = "id, tenant_id, kind, name, country,"
            + " tax_identifier_encrypted IS NOT NULL AS has_tax_identifier, created_at";
    private static final String INTEREST = """
            id, tenant_id, owned_legal_entity_id, owner_legal_entity_id, owner_external_party_id, capital_percentage,
            voting_percentage, share_class, valid_from, valid_to, created_at""";

    private final JdbcClient jdbc;

    public OwnershipRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insertParty(ExternalParty p, byte @Nullable [] encryptedTaxIdentifier) {
        jdbc.sql("INSERT INTO external_party (id, tenant_id, kind, name, country, tax_identifier_encrypted, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)")
                .params(
                        p.id(),
                        p.tenantId().value(),
                        p.kind().name(),
                        p.name(),
                        p.country() == null ? null : p.country().value(),
                        encryptedTaxIdentifier,
                        OffsetDateTime.ofInstant(p.createdAt(), ZoneOffset.UTC))
                .update();
    }

    public Optional<ExternalParty> findParty(UUID id) {
        return jdbc.sql("SELECT " + PARTY + " FROM external_party WHERE id = ?")
                .param(id)
                .query(this::party)
                .optional();
    }

    public List<ExternalParty> parties() {
        return jdbc.sql("SELECT " + PARTY + " FROM external_party ORDER BY name LIMIT 1000")
                .query(this::party)
                .list();
    }

    public Map<UUID, String> partyNames(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jdbc
                .sql("SELECT id, name FROM external_party WHERE id = ANY(?)")
                .param(ids.toArray(UUID[]::new))
                .query((rs, row) -> Map.entry(rs.getObject("id", UUID.class), rs.getString("name")))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Map<UUID, String> entityNames(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jdbc
                .sql("SELECT id, legal_name FROM legal_entity WHERE id = ANY(?)")
                .param(ids.toArray(UUID[]::new))
                .query((rs, row) -> Map.entry(rs.getObject("id", UUID.class), rs.getString("legal_name")))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** Trava as participações do tenant: as regras (soma e ciclo) olham a cadeia inteira. */
    public void lock(TenantId tenant) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")
                .param("ownership:" + tenant)
                .query((rs, row) -> 1)
                .list();
    }

    public void insert(OwnershipInterest i) {
        jdbc.sql("INSERT INTO ownership_interest (" + INTEREST + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(
                        i.id(),
                        i.tenantId().value(),
                        i.ownedLegalEntityId(),
                        i.holder().type() == HolderType.LEGAL_ENTITY
                                ? i.holder().id()
                                : null,
                        i.holder().type() == HolderType.EXTERNAL_PARTY
                                ? i.holder().id()
                                : null,
                        i.capitalPercentage(),
                        i.votingPercentage(),
                        i.shareClass(),
                        i.period().validFrom(),
                        i.period().validTo(),
                        OffsetDateTime.ofInstant(i.createdAt(), ZoneOffset.UTC))
                .update();
    }

    public void end(UUID id, LocalDate on) {
        jdbc.sql("UPDATE ownership_interest SET valid_to = ? WHERE id = ?")
                .params(on, id)
                .update();
    }

    public Optional<OwnershipInterest> find(UUID id) {
        return jdbc.sql("SELECT " + INTEREST + " FROM ownership_interest WHERE id = ?")
                .param(id)
                .query(this::interest)
                .optional();
    }

    /** Participações vigentes em algum momento a partir de {@code from}. */
    public List<OwnershipInterest> activeFrom(LocalDate from) {
        return jdbc.sql("SELECT " + INTEREST + " FROM ownership_interest WHERE valid_to IS NULL OR valid_to > ?")
                .param(from)
                .query(this::interest)
                .list();
    }

    public List<OwnershipInterest> activeOn(LocalDate date) {
        return jdbc.sql("SELECT " + INTEREST + " FROM ownership_interest"
                        + " WHERE valid_from <= ? AND (valid_to IS NULL OR valid_to > ?)")
                .params(date, date)
                .query(this::interest)
                .list();
    }

    private ExternalParty party(ResultSet rs, int row) throws SQLException {
        String country = rs.getString("country");
        return new ExternalParty(
                rs.getObject("id", UUID.class),
                new TenantId(rs.getObject("tenant_id", UUID.class)),
                ExternalParty.Kind.valueOf(rs.getString("kind")),
                rs.getString("name"),
                country == null ? null : new CountryCode(country),
                rs.getBoolean("has_tax_identifier"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private OwnershipInterest interest(ResultSet rs, int row) throws SQLException {
        UUID entityOwner = rs.getObject("owner_legal_entity_id", UUID.class);
        Holder holder = entityOwner != null
                ? new Holder(HolderType.LEGAL_ENTITY, entityOwner)
                : new Holder(HolderType.EXTERNAL_PARTY, rs.getObject("owner_external_party_id", UUID.class));
        return new OwnershipInterest(
                rs.getObject("id", UUID.class),
                new TenantId(rs.getObject("tenant_id", UUID.class)),
                rs.getObject("owned_legal_entity_id", UUID.class),
                holder,
                rs.getBigDecimal("capital_percentage"),
                rs.getBigDecimal("voting_percentage"),
                rs.getString("share_class"),
                new EffectivePeriod(
                        rs.getObject("valid_from", LocalDate.class), rs.getObject("valid_to", LocalDate.class)),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
