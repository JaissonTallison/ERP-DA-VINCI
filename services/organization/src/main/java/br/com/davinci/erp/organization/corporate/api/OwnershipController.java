package br.com.davinci.erp.organization.corporate.api;

import br.com.davinci.erp.organization.corporate.application.OwnershipService;
import br.com.davinci.erp.organization.corporate.domain.ExternalParty;
import br.com.davinci.erp.organization.corporate.domain.OwnershipGraph;
import br.com.davinci.erp.organization.corporate.domain.OwnershipInterest;
import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import br.com.davinci.erp.organization.shared.web.LifecycleBody;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
class OwnershipController {

    private final OwnershipService service;

    OwnershipController(OwnershipService service) {
        this.service = service;
    }

    record CreatePartyRequest(
            @NotBlank String kind,
            @NotBlank @Size(max = 200) String name,
            @Nullable @Size(min = 2, max = 2) String country,
            @Nullable @Size(max = 40) String taxIdentifier) {}

    record PartyResponse(
            UUID id, String kind, String name, @Nullable String country, boolean hasTaxIdentifier) {

        static PartyResponse from(ExternalParty p) {
            return new PartyResponse(
                    p.id(),
                    p.kind().name(),
                    p.name(),
                    p.country() == null ? null : p.country().value(),
                    p.hasTaxIdentifier());
        }
    }

    record PartyList(List<PartyResponse> items) {}

    record CreateInterestRequest(
            @Nullable UUID ownerLegalEntityId,
            @Nullable UUID ownerExternalPartyId,
            @NotNull BigDecimal capitalPercentage,
            @NotNull BigDecimal votingPercentage,
            @Nullable @Size(max = 40) String shareClass,
            @NotNull LocalDate validFrom) {}

    record InterestResponse(
            UUID id,
            UUID ownedLegalEntityId,
            String ownedName,
            String holderType,
            UUID holderId,
            String holderName,
            BigDecimal capitalPercentage,
            BigDecimal votingPercentage,
            @Nullable String shareClass,
            LocalDate validFrom,
            @Nullable LocalDate validTo) {

        static InterestResponse from(OwnershipInterest i, String holderName, String ownedName) {
            return new InterestResponse(
                    i.id(),
                    i.ownedLegalEntityId(),
                    ownedName,
                    i.holder().type().name(),
                    i.holder().id(),
                    holderName,
                    i.capitalPercentage(),
                    i.votingPercentage(),
                    i.shareClass(),
                    i.period().validFrom(),
                    i.period().validTo());
        }
    }

    record InterestList(LocalDate asOf, List<InterestResponse> items) {}

    record HolderResponse(String holderType, UUID holderId, String name, int depth, BigDecimal effectiveCapital) {}

    record ControllerResponse(String holderType, UUID holderId, String name) {}

    record ChartResponse(
            LocalDate asOf,
            UUID legalEntityId,
            String name,
            List<HolderResponse> holders,
            List<InterestResponse> edges,
            List<ControllerResponse> controlChain,
            @Nullable ControllerResponse ultimateController) {}

    @PostMapping("/v1/external-parties")
    ResponseEntity<PartyResponse> createParty(@Valid @RequestBody CreatePartyRequest body) {
        ExternalParty p = service.createParty(
                new OwnershipService.CreateParty(body.kind(), body.name(), body.country(), body.taxIdentifier()));
        return ResponseEntity.created(URI.create("/v1/external-parties/" + p.id()))
                .body(PartyResponse.from(p));
    }

    @GetMapping("/v1/external-parties")
    PartyList parties() {
        return new PartyList(service.parties().stream().map(PartyResponse::from).toList());
    }

    @PostMapping("/v1/legal-entities/{id}/ownership")
    ResponseEntity<InterestResponse> create(@PathVariable UUID id, @Valid @RequestBody CreateInterestRequest body) {
        OwnershipInterest i = service.create(new OwnershipService.CreateInterest(
                id,
                body.ownerLegalEntityId(),
                body.ownerExternalPartyId(),
                body.capitalPercentage(),
                body.votingPercentage(),
                body.shareClass(),
                body.validFrom()));
        return ResponseEntity.created(URI.create("/v1/ownership-interests/" + i.id()))
                .body(InterestResponse.from(i, "", ""));
    }

    @PostMapping("/v1/ownership-interests/{id}/end")
    InterestResponse end(@PathVariable UUID id, @Valid @RequestBody LifecycleBody body) {
        if (body.effectiveOn() == null) {
            throw new InvalidValueException("effectiveOn", "Informe a data de fim");
        }
        return InterestResponse.from(service.end(id, body.effectiveOn()), "", "");
    }

    @GetMapping("/v1/legal-entities/{id}/ownership")
    InterestList direct(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "owners") String direction,
            @RequestParam @Nullable LocalDate asOf) {
        boolean owners = switch (direction) {
            case "owners" -> true;
            case "holdings" -> false;
            default -> throw new InvalidValueException("direction", "Use owners ou holdings");
        };
        var found = service.direct(id, owners, asOf);
        LocalDate date = asOf == null ? LocalDate.now(ZoneOffset.UTC) : asOf;
        return new InterestList(
                date,
                found.stream()
                        .map(n -> InterestResponse.from(n.interest(), n.holderName(), n.ownedName()))
                        .toList());
    }

    @GetMapping("/v1/legal-entities/{id}/ownership-chart")
    ChartResponse chart(@PathVariable UUID id, @RequestParam @Nullable LocalDate asOf) {
        OwnershipService.NamedChart named = service.chart(id, asOf);
        OwnershipGraph.Chart c = named.chart();
        var names = named.names();
        return new ChartResponse(
                named.asOf(),
                c.legalEntityId(),
                names.getOrDefault(c.legalEntityId(), ""),
                c.holders().stream()
                        .map(h -> new HolderResponse(
                                h.holder().type().name(),
                                h.holder().id(),
                                names.getOrDefault(h.holder().id(), ""),
                                h.depth(),
                                h.effectiveCapital()))
                        .toList(),
                c.edges().stream()
                        .map(i -> InterestResponse.from(
                                i,
                                names.getOrDefault(i.holder().id(), ""),
                                names.getOrDefault(i.ownedLegalEntityId(), "")))
                        .toList(),
                c.controlChain().stream()
                        .map(h -> new ControllerResponse(h.type().name(), h.id(), names.getOrDefault(h.id(), "")))
                        .toList(),
                c.ultimateController() == null
                        ? null
                        : new ControllerResponse(
                                c.ultimateController().type().name(),
                                c.ultimateController().id(),
                                names.getOrDefault(c.ultimateController().id(), "")));
    }
}
