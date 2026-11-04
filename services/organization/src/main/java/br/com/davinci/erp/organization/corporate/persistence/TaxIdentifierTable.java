package br.com.davinci.erp.organization.corporate.persistence;

import br.com.davinci.erp.organization.corporate.domain.TaxIdentifierRecord;
import br.com.davinci.erp.organization.shared.domain.ConflictException;
import br.com.davinci.erp.organization.shared.domain.EffectivePeriod;
import br.com.davinci.erp.organization.shared.domain.TaxIdentifier;
import br.com.davinci.erp.organization.shared.domain.TaxScheme;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Acesso a uma tabela de identificadores fiscais com vigência ({@code tax_identifier} da entidade ou
 * {@code establishment_tax_identifier}). Nomes de tabela e coluna são constantes do código, nunca entrada externa.
 */
final class TaxIdentifierTable {

    private final JdbcClient jdbc;
    private final String table;
    private final String ownerColumn;

    TaxIdentifierTable(JdbcClient jdbc, String table, String ownerColumn) {
        this.jdbc = jdbc;
        this.table = table;
        this.ownerColumn = ownerColumn;
    }

    Map<UUID, List<TaxIdentifierRecord>> load(Collection<UUID> owners) {
        Map<UUID, List<TaxIdentifierRecord>> result = new LinkedHashMap<>();
        owners.forEach(id -> result.put(id, new ArrayList<>()));
        if (owners.isEmpty()) {
            return result;
        }
        jdbc.sql("SELECT %1$s, id, scheme, value, is_primary, valid_from, valid_to FROM %2$s WHERE %1$s = ANY(?)"
                                .formatted(ownerColumn, table)
                        + " ORDER BY valid_from, scheme")
                .param(owners.toArray(UUID[]::new))
                .query((rs, row) -> {
                    result.get(rs.getObject(ownerColumn, UUID.class))
                            .add(new TaxIdentifierRecord(
                                    rs.getObject("id", UUID.class),
                                    new TaxIdentifier(TaxScheme.valueOf(rs.getString("scheme")), rs.getString("value")),
                                    rs.getBoolean("is_primary"),
                                    new EffectivePeriod(
                                            rs.getObject("valid_from", LocalDate.class), Rows.date(rs, "valid_to"))));
                    return row;
                })
                .list();
        return result;
    }

    void insert(TenantId tenant, UUID owner, TaxIdentifierRecord record) {
        try {
            jdbc.sql("INSERT INTO %s (id, tenant_id, %s, scheme, value, is_primary, valid_from, valid_to)"
                                    .formatted(table, ownerColumn)
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                    .params(
                            record.id(),
                            tenant.value(),
                            owner,
                            record.identifier().scheme().name(),
                            record.identifier().value(),
                            record.primary(),
                            record.period().validFrom(),
                            record.period().validTo())
                    .update();
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
    }

    void end(UUID id, LocalDate on) {
        jdbc.sql("UPDATE %s SET valid_to = ? WHERE id = ?".formatted(table))
                .params(on, id)
                .update();
    }

    /** Violações de unicidade com vigência viram conflitos com código estável. */
    private RuntimeException translate(DataIntegrityViolationException e) {
        String message = String.valueOf(e.getMessage());
        if (message.contains(table + "_unique_value")) {
            return new ConflictException(
                    "duplicate-tax-identifier", "Identificador fiscal já vigente em outro registro deste tenant");
        }
        if (message.contains(table + "_one_primary")) {
            return new ConflictException(
                    "overlapping-period", "Já existe identificador fiscal principal vigente no período");
        }
        return e;
    }
}
