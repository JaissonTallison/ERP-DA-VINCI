package br.com.davinci.erp.organization.corporate.api;

import br.com.davinci.erp.organization.corporate.application.EstablishmentView;
import br.com.davinci.erp.organization.corporate.application.LegalEntityView;
import br.com.davinci.erp.organization.corporate.domain.EconomicGroup;
import br.com.davinci.erp.organization.corporate.domain.Establishment;
import br.com.davinci.erp.organization.corporate.domain.LegalEntity;
import br.com.davinci.erp.organization.shared.domain.CountryCode;
import br.com.davinci.erp.organization.shared.domain.PostalAddress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Corpos de requisição e resposta v1 (contracts/http/organization/v1/openapi.yaml). */
final class Representations {

    private Representations() {}

    record CreateEconomicGroupRequest(
            @NotBlank @Nullable String code,
            @NotBlank @Nullable String name,
            @NotBlank @Nullable String referenceCurrency,
            @NotBlank @Nullable String referenceTimezone) {}

    record EconomicGroupResponse(
            UUID id,
            String code,
            String name,
            String referenceCurrency,
            String referenceTimezone,
            String status,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        static EconomicGroupResponse from(EconomicGroup g) {
            return new EconomicGroupResponse(
                    g.id(),
                    g.code().value(),
                    g.name(),
                    g.referenceCurrency().getCurrencyCode(),
                    g.referenceTimezone().getId(),
                    g.status().name(),
                    g.createdAt(),
                    g.updatedAt(),
                    g.version());
        }
    }

    record EconomicGroupPage(
            List<EconomicGroupResponse> items, @Nullable String nextCursor) {}

    record AddressRequest(
            @NotBlank @Nullable String country,
            @NotNull @Size(min = 1, max = 4) @Nullable List<String> addressLines,
            @NotBlank @Nullable String locality,
            @Nullable String administrativeArea,
            @Nullable String postalCode) {

        PostalAddress toDomain() {
            return new PostalAddress(
                    new CountryCode(String.valueOf(country)),
                    List.copyOf(addressLines == null ? List.of() : addressLines),
                    String.valueOf(locality),
                    administrativeArea,
                    postalCode);
        }
    }

    record AddressResponse(
            String country,
            List<String> addressLines,
            String locality,
            @Nullable String administrativeArea,
            @Nullable String postalCode) {

        static AddressResponse from(PostalAddress a) {
            return new AddressResponse(
                    a.country().value(), a.addressLines(), a.locality(), a.administrativeArea(), a.postalCode());
        }
    }

    record TaxIdentifierRequest(
            @NotBlank @Nullable String scheme,
            @NotBlank @Nullable String value,
            @Nullable Boolean primary,
            @Nullable LocalDate validFrom) {}

    record CreateLegalEntityRequest(
            @NotBlank @Nullable String code,
            @NotBlank @Nullable String legalName,
            @Nullable String tradeName,
            @NotBlank @Nullable String country,
            @Nullable String subdivision,
            @Nullable String legalForm,
            @NotBlank @Nullable String functionalCurrency,
            @NotBlank @Nullable String timezone,
            @NotBlank @Nullable String defaultLocale,
            @Min(1) @Max(12) @Nullable Integer fiscalYearStartMonth,
            @NotNull @Valid @Nullable AddressRequest registeredAddress,
            @Size(max = 10) @Valid @Nullable List<TaxIdentifierRequest> taxIdentifiers,
            @Nullable UUID economicGroupId,
            @Nullable String status,
            @Nullable LocalDate effectiveOn) {}

    record GroupAssignmentRequest(
            @Nullable UUID economicGroupId,
            @NotNull @Nullable LocalDate effectiveOn) {}

    record GroupAssignmentResponse(
            UUID economicGroupId,
            LocalDate validFrom,
            @Nullable LocalDate validTo) {}

    record TaxIdentifierResponse(
            UUID id,
            String scheme,
            String value,
            boolean primary,
            LocalDate validFrom,
            @Nullable LocalDate validTo) {}

    record LegalEntityResponse(
            UUID id,
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
            AddressResponse registeredAddress,
            @Nullable LocalDate incorporatedOn,
            @Nullable LocalDate closedOn,
            String status,
            LocalDate asOf,
            @Nullable UUID economicGroupId,
            List<GroupAssignmentResponse> groupAssignments,
            List<TaxIdentifierResponse> taxIdentifiers,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        static LegalEntityResponse from(LegalEntityView view) {
            LegalEntity e = view.entity();
            return new LegalEntityResponse(
                    e.id(),
                    e.code().value(),
                    e.legalName(),
                    e.tradeName(),
                    e.country().value(),
                    e.subdivision(),
                    e.legalForm(),
                    e.functionalCurrency().getCurrencyCode(),
                    e.timezone().getId(),
                    e.defaultLocale().toLanguageTag(),
                    e.fiscalYearStartMonth(),
                    AddressResponse.from(e.registeredAddress()),
                    e.incorporatedOn(),
                    e.closedOn(),
                    e.status().name(),
                    view.asOf(),
                    view.economicGroupId().orElse(null),
                    view.groupAssignments().stream()
                            .map(a -> new GroupAssignmentResponse(
                                    a.economicGroupId(),
                                    a.period().validFrom(),
                                    a.period().validTo()))
                            .toList(),
                    view.taxIdentifiers().stream()
                            .map(t -> new TaxIdentifierResponse(
                                    t.id(),
                                    t.identifier().scheme().name(),
                                    t.identifier().value(),
                                    t.primary(),
                                    t.period().validFrom(),
                                    t.period().validTo()))
                            .toList(),
                    e.createdAt(),
                    e.updatedAt(),
                    e.version());
        }
    }

    record LegalEntityPage(
            List<LegalEntityResponse> items, @Nullable String nextCursor) {}

    record OpenEstablishmentRequest(
            @NotNull @Nullable UUID legalEntityId,
            @NotBlank @Nullable String code,
            @NotBlank @Nullable String name,
            @NotBlank @Nullable String type,
            @NotNull @Valid @Nullable AddressRequest address,
            @Nullable String timezone,
            @Size(max = 10) @Valid @Nullable List<TaxIdentifierRequest> taxIdentifiers,
            @Nullable LocalDate openedOn) {}

    record EstablishmentResponse(
            UUID id,
            UUID legalEntityId,
            String code,
            String name,
            String type,
            AddressResponse address,
            String timezone,
            String status,
            LocalDate openedOn,
            @Nullable LocalDate closedOn,
            List<TaxIdentifierResponse> taxIdentifiers,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        static EstablishmentResponse from(EstablishmentView view) {
            Establishment e = view.establishment();
            return new EstablishmentResponse(
                    e.id(),
                    e.legalEntityId(),
                    e.code().value(),
                    e.name(),
                    e.type().name(),
                    AddressResponse.from(e.address()),
                    e.timezone().getId(),
                    e.status().name(),
                    e.openedOn(),
                    e.closedOn(),
                    view.taxIdentifiers().stream()
                            .map(t -> new TaxIdentifierResponse(
                                    t.id(),
                                    t.identifier().scheme().name(),
                                    t.identifier().value(),
                                    t.primary(),
                                    t.period().validFrom(),
                                    t.period().validTo()))
                            .toList(),
                    e.createdAt(),
                    e.updatedAt(),
                    e.version());
        }
    }

    record EstablishmentPage(
            List<EstablishmentResponse> items, @Nullable String nextCursor) {}
}
