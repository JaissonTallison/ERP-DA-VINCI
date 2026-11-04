package br.com.davinci.erp.organization.corporate.domain;

import br.com.davinci.erp.organization.shared.domain.BusinessCode;
import br.com.davinci.erp.organization.shared.domain.ConflictException;
import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import br.com.davinci.erp.organization.shared.domain.PostalAddress;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Local físico ou fiscal de uma entidade jurídica (matriz, filial, fábrica, loja, depósito). Pertence a uma única
 * entidade, para sempre: mudar de entidade é encerrar e abrir outro. Transições idempotentes.
 */
public record Establishment(
        UUID id,
        TenantId tenantId,
        UUID legalEntityId,
        BusinessCode code,
        String name,
        EstablishmentType type,
        PostalAddress address,
        ZoneId timezone,
        EstablishmentStatus status,
        LocalDate openedOn,
        @Nullable LocalDate closedOn,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public Establishment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(legalEntityId, "legalEntityId");
        Objects.requireNonNull(code, "code");
        name = Texts.required("name", name, 200);
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(timezone, "timezone");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(openedOn, "openedOn");
    }

    /** Dados alteráveis do estabelecimento. */
    public record Profile(String name, EstablishmentType type, PostalAddress address, ZoneId timezone) {}

    /** Abre o estabelecimento; a entidade precisa estar ativa e o endereço no país dela. */
    public static Establishment open(
            UUID id, LegalEntity entity, BusinessCode code, Profile profile, LocalDate on, Instant now) {
        requireEntityActive(entity);
        requireSameCountry(entity, profile.address());
        return new Establishment(
                id,
                entity.tenantId(),
                entity.id(),
                code,
                profile.name(),
                profile.type(),
                profile.address(),
                profile.timezone(),
                EstablishmentStatus.ACTIVE,
                on,
                null,
                now,
                now,
                0);
    }

    public Profile profile() {
        return new Profile(name, type, address, timezone);
    }

    public Establishment update(Profile changes, LegalEntity entity, Instant now) {
        if (status == EstablishmentStatus.CLOSED) {
            throw new InvalidTransitionException("alterar um estabelecimento", status);
        }
        requireSameCountry(entity, changes.address());
        if (changes.equals(profile())) {
            return this;
        }
        return with(changes, status, closedOn, now);
    }

    public Establishment suspend(Instant now) {
        return switch (status) {
            case SUSPENDED -> this;
            case ACTIVE -> with(profile(), EstablishmentStatus.SUSPENDED, null, now);
            case CLOSED -> throw new InvalidTransitionException("suspender", status);
        };
    }

    public Establishment reactivate(LegalEntity entity, Instant now) {
        return switch (status) {
            case ACTIVE -> this;
            case SUSPENDED -> {
                requireEntityActive(entity);
                yield with(profile(), EstablishmentStatus.ACTIVE, null, now);
            }
            case CLOSED -> throw new InvalidTransitionException("reativar", status);
        };
    }

    public Establishment close(LocalDate on, Instant now) {
        return switch (status) {
            case CLOSED -> this;
            case ACTIVE, SUSPENDED -> {
                if (on.isBefore(openedOn)) {
                    throw new InvalidValueException("effectiveOn", "O encerramento não pode ser anterior à abertura");
                }
                yield with(profile(), EstablishmentStatus.CLOSED, on, now);
            }
        };
    }

    private Establishment with(Profile p, EstablishmentStatus newStatus, @Nullable LocalDate closed, Instant now) {
        return new Establishment(
                id,
                tenantId,
                legalEntityId,
                code,
                p.name(),
                p.type(),
                p.address(),
                p.timezone(),
                newStatus,
                openedOn,
                closed,
                createdAt,
                now,
                version + 1);
    }

    private static void requireEntityActive(LegalEntity entity) {
        if (entity.status() != LegalEntityStatus.ACTIVE) {
            throw new ConflictException(
                    "legal-entity-not-active",
                    "A entidade jurídica precisa estar ACTIVE para abrir ou reativar estabelecimento (está "
                            + entity.status() + ")");
        }
    }

    private static void requireSameCountry(LegalEntity entity, PostalAddress address) {
        if (!address.country().equals(entity.country())) {
            throw new InvalidValueException(
                    "address.country",
                    "O estabelecimento fica no país da entidade jurídica (" + entity.country() + ")");
        }
    }
}
