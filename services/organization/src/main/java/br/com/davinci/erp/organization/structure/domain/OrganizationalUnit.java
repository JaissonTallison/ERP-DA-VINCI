package br.com.davinci.erp.organization.structure.domain;

import br.com.davinci.erp.organization.corporate.domain.InvalidTransitionException;
import br.com.davinci.erp.organization.shared.domain.BusinessCode;
import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import br.com.davinci.erp.organization.shared.domain.LocalizedText;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unidade de gestão (unidade de negócio, diretoria, departamento, unidade operacional…). Não guarda o pai: a posição
 * fica em cada estrutura ({@link StructureLink}), então a mesma unidade pode ter pais diferentes em estruturas
 * diferentes.
 */
public record OrganizationalUnit(
        UUID id,
        TenantId tenantId,
        BusinessCode code,
        LocalizedText name,
        UUID unitTypeId,
        @Nullable UUID legalEntityId,
        @Nullable UUID establishmentId,
        UnitStatus status,
        LocalDate validFrom,
        @Nullable LocalDate closedOn,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public OrganizationalUnit {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(unitTypeId, "unitTypeId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(validFrom, "validFrom");
        if (establishmentId != null && legalEntityId == null) {
            throw new InvalidValueException(
                    "establishmentId", "Unidade com estabelecimento precisa da entidade jurídica dele");
        }
    }

    /** Dados alteráveis; a coerência entidade × estabelecimento é verificada pelo caso de uso. */
    public record Profile(
            LocalizedText name,
            UUID unitTypeId,
            @Nullable UUID legalEntityId,
            @Nullable UUID establishmentId) {}

    public static OrganizationalUnit create(
            UUID id, TenantId tenant, BusinessCode code, Profile profile, LocalDate validFrom, Instant now) {
        return new OrganizationalUnit(
                id,
                tenant,
                code,
                profile.name(),
                profile.unitTypeId(),
                profile.legalEntityId(),
                profile.establishmentId(),
                UnitStatus.ACTIVE,
                validFrom,
                null,
                now,
                now,
                0);
    }

    public Profile profile() {
        return new Profile(name, unitTypeId, legalEntityId, establishmentId);
    }

    public OrganizationalUnit update(Profile changes, Instant now) {
        if (status == UnitStatus.CLOSED) {
            throw new InvalidTransitionException("alterar uma unidade", status);
        }
        return changes.equals(profile()) ? this : with(changes, status, closedOn, now);
    }

    public OrganizationalUnit deactivate(Instant now) {
        return switch (status) {
            case INACTIVE -> this;
            case ACTIVE -> with(profile(), UnitStatus.INACTIVE, null, now);
            case CLOSED -> throw new InvalidTransitionException("desativar", status);
        };
    }

    public OrganizationalUnit reactivate(Instant now) {
        return switch (status) {
            case ACTIVE -> this;
            case INACTIVE -> with(profile(), UnitStatus.ACTIVE, null, now);
            case CLOSED -> throw new InvalidTransitionException("reativar", status);
        };
    }

    /** Encerramento; o caso de uso garante que não há filhos a partir de {@code on} em nenhuma estrutura. */
    public OrganizationalUnit close(LocalDate on, Instant now) {
        return switch (status) {
            case CLOSED -> this;
            case ACTIVE, INACTIVE -> {
                if (on.isBefore(validFrom)) {
                    throw new InvalidValueException(
                            "effectiveOn", "O encerramento não pode ser anterior ao início da unidade");
                }
                yield with(profile(), UnitStatus.CLOSED, on, now);
            }
        };
    }

    /** Nova versão sem mudar atributos: a posição da unidade numa estrutura mudou (eventos levam versão crescente). */
    public OrganizationalUnit touch(Instant now) {
        return with(profile(), status, closedOn, now);
    }

    public boolean usableOn(LocalDate date) {
        return status != UnitStatus.CLOSED && !date.isBefore(validFrom);
    }

    private OrganizationalUnit with(Profile p, UnitStatus newStatus, @Nullable LocalDate closed, Instant now) {
        return new OrganizationalUnit(
                id,
                tenantId,
                code,
                p.name(),
                p.unitTypeId(),
                p.legalEntityId(),
                p.establishmentId(),
                newStatus,
                validFrom,
                closed,
                createdAt,
                now,
                version + 1);
    }
}
