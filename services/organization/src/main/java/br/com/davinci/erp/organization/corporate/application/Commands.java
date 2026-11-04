package br.com.davinci.erp.organization.corporate.application;

import br.com.davinci.erp.organization.shared.domain.PostalAddress;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Comandos do módulo corporativo (já com valores validados pela API). */
public final class Commands {

    private Commands() {}

    public record CreateEconomicGroup(String code, String name, String referenceCurrency, String referenceTimezone) {}

    public record UpdateEconomicGroup(
            @Nullable String name,
            @Nullable String referenceCurrency,
            @Nullable String referenceTimezone) {}

    public record NewTaxIdentifier(
            String scheme,
            String value,
            boolean primary,
            @Nullable LocalDate validFrom) {}

    public record CreateLegalEntity(
            String code,
            String legalName,
            @Nullable String tradeName,
            String country,
            @Nullable String subdivision,
            @Nullable String legalForm,
            String functionalCurrency,
            String timezone,
            String defaultLocale,
            int fiscalYearStartMonth,
            PostalAddress registeredAddress,
            List<NewTaxIdentifier> taxIdentifiers,
            @Nullable UUID economicGroupId,
            boolean active,
            @Nullable LocalDate effectiveOn) {}

    public record OpenEstablishment(
            UUID legalEntityId,
            String code,
            String name,
            String type,
            PostalAddress address,
            @Nullable String timezone,
            List<NewTaxIdentifier> taxIdentifiers,
            @Nullable LocalDate openedOn) {}

    public record UpdateEstablishment(
            @Nullable String name,
            @Nullable String type,
            @Nullable PostalAddress address,
            @Nullable String timezone) {}

    /** Campos presentes no merge-patch; ausente = não muda. {@code tradeName}/{@code legalForm} aceitam limpar. */
    public record UpdateLegalEntity(
            @Nullable String legalName,
            boolean tradeNamePresent,
            @Nullable String tradeName,
            boolean legalFormPresent,
            @Nullable String legalForm,
            @Nullable String functionalCurrency,
            @Nullable String timezone,
            @Nullable String defaultLocale,
            @Nullable Integer fiscalYearStartMonth,
            @Nullable PostalAddress registeredAddress) {}
}
