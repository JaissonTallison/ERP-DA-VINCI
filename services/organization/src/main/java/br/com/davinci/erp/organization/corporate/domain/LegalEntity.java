package br.com.davinci.erp.organization.corporate.domain;

import br.com.davinci.erp.organization.shared.domain.BusinessCode;
import br.com.davinci.erp.organization.shared.domain.CountryCode;
import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import br.com.davinci.erp.organization.shared.domain.PostalAddress;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Entidade jurídica: pessoa jurídica num país, com moeda funcional, fuso, idioma e calendário fiscal. Imutável; as
 * transições de ciclo de vida são idempotentes (repetir devolve a mesma instância).
 */
public record LegalEntity(
        UUID id,
        TenantId tenantId,
        BusinessCode code,
        String legalName,
        @Nullable String tradeName,
        CountryCode country,
        @Nullable String subdivision,
        @Nullable String legalForm,
        Currency functionalCurrency,
        ZoneId timezone,
        Locale defaultLocale,
        int fiscalYearStartMonth,
        PostalAddress registeredAddress,
        @Nullable LocalDate incorporatedOn,
        @Nullable LocalDate closedOn,
        LegalEntityStatus status,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    private static final Pattern SUBDIVISION = Pattern.compile("[A-Z]{2}-[A-Z0-9]{1,3}");

    public LegalEntity {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(code, "code");
        legalName = Texts.required("legalName", legalName, 200);
        tradeName = Texts.optional("tradeName", tradeName, 200);
        Objects.requireNonNull(country, "country");
        if (subdivision != null
                && (!SUBDIVISION.matcher(subdivision).matches() || !subdivision.startsWith(country.value() + "-"))) {
            throw new InvalidValueException(
                    "subdivision", "Subdivisão inválida (ISO 3166-2 do próprio país, ex.: BR-SP)");
        }
        legalForm = Texts.optional("legalForm", legalForm, 40);
        Objects.requireNonNull(functionalCurrency, "functionalCurrency");
        Objects.requireNonNull(timezone, "timezone");
        Objects.requireNonNull(defaultLocale, "defaultLocale");
        if (fiscalYearStartMonth < 1 || fiscalYearStartMonth > 12) {
            throw new InvalidValueException(
                    "fiscalYearStartMonth", "Mês de início do exercício deve estar entre 1 e 12");
        }
        Objects.requireNonNull(registeredAddress, "registeredAddress");
        if (!registeredAddress.country().equals(country)) {
            throw new InvalidValueException("registeredAddress.country", "A sede legal fica no país da entidade");
        }
        Objects.requireNonNull(status, "status");
    }

    /** Dados de cadastro, comuns à criação e à alteração. */
    public record Profile(
            String legalName,
            @Nullable String tradeName,
            @Nullable String legalForm,
            Currency functionalCurrency,
            ZoneId timezone,
            Locale defaultLocale,
            int fiscalYearStartMonth,
            PostalAddress registeredAddress) {}

    public static LegalEntity plan(
            UUID id,
            TenantId tenant,
            BusinessCode code,
            CountryCode country,
            @Nullable String subdivision,
            Profile profile,
            Instant now) {
        return new LegalEntity(
                id,
                tenant,
                code,
                profile.legalName(),
                profile.tradeName(),
                country,
                subdivision,
                profile.legalForm(),
                profile.functionalCurrency(),
                profile.timezone(),
                profile.defaultLocale(),
                profile.fiscalYearStartMonth(),
                profile.registeredAddress(),
                null,
                null,
                LegalEntityStatus.PLANNED,
                now,
                now,
                0);
    }

    public Profile profile() {
        return new Profile(
                legalName,
                tradeName,
                legalForm,
                functionalCurrency,
                timezone,
                defaultLocale,
                fiscalYearStartMonth,
                registeredAddress);
    }

    /** Moeda funcional e calendário fiscal só mudam antes da ativação (afetam a contabilidade já lançada). */
    public LegalEntity update(Profile changes, Instant now) {
        if (status == LegalEntityStatus.CLOSED) {
            throw new InvalidTransitionException("alterar uma entidade", status);
        }
        if (status != LegalEntityStatus.PLANNED
                && (!changes.functionalCurrency().equals(functionalCurrency)
                        || changes.fiscalYearStartMonth() != fiscalYearStartMonth)) {
            throw new InvalidValueException(
                    "functionalCurrency",
                    "Moeda funcional e início do exercício só mudam enquanto a entidade está PLANNED");
        }
        if (changes.equals(profile())) {
            return this;
        }
        return with(changes, status, incorporatedOn, closedOn, now);
    }

    public LegalEntity activate(LocalDate on, Instant now) {
        return switch (status) {
            case ACTIVE -> this;
            case PLANNED -> with(profile(), LegalEntityStatus.ACTIVE, on, null, now);
            case SUSPENDED, CLOSED -> throw new InvalidTransitionException("ativar", status);
        };
    }

    public LegalEntity suspend(Instant now) {
        return switch (status) {
            case SUSPENDED -> this;
            case ACTIVE -> with(profile(), LegalEntityStatus.SUSPENDED, incorporatedOn, null, now);
            case PLANNED, CLOSED -> throw new InvalidTransitionException("suspender", status);
        };
    }

    public LegalEntity reactivate(Instant now) {
        return switch (status) {
            case ACTIVE -> this;
            case SUSPENDED -> with(profile(), LegalEntityStatus.ACTIVE, incorporatedOn, null, now);
            case PLANNED, CLOSED -> throw new InvalidTransitionException("reativar", status);
        };
    }

    public LegalEntity close(LocalDate on, Instant now) {
        return switch (status) {
            case CLOSED -> this;
            case ACTIVE, SUSPENDED -> {
                if (incorporatedOn != null && on.isBefore(incorporatedOn)) {
                    throw new InvalidValueException(
                            "effectiveOn", "O encerramento não pode ser anterior à constituição");
                }
                yield with(profile(), LegalEntityStatus.CLOSED, incorporatedOn, on, now);
            }
            case PLANNED -> throw new InvalidTransitionException("encerrar (entidade ainda não constituída)", status);
        };
    }

    private LegalEntity with(
            Profile p,
            LegalEntityStatus newStatus,
            @Nullable LocalDate incorporated,
            @Nullable LocalDate closed,
            Instant now) {
        return new LegalEntity(
                id,
                tenantId,
                code,
                p.legalName(),
                p.tradeName(),
                country,
                subdivision,
                p.legalForm(),
                p.functionalCurrency(),
                p.timezone(),
                p.defaultLocale(),
                p.fiscalYearStartMonth(),
                p.registeredAddress(),
                incorporated,
                closed,
                newStatus,
                createdAt,
                now,
                version + 1);
    }
}
