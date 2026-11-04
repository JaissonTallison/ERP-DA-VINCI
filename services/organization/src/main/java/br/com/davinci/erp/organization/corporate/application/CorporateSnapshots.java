package br.com.davinci.erp.organization.corporate.application;

import br.com.davinci.erp.organization.corporate.domain.EconomicGroup;
import br.com.davinci.erp.organization.corporate.domain.Establishment;
import br.com.davinci.erp.organization.corporate.domain.LegalEntity;
import br.com.davinci.erp.organization.corporate.domain.TaxIdentifierRecord;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Estado completo dos agregados para eventos (contracts/events/organization/v1) e auditoria (antes/depois). */
@Component
class CorporateSnapshots {

    private final JsonMapper json;

    CorporateSnapshots(JsonMapper json) {
        this.json = json;
    }

    ObjectNode economicGroup(EconomicGroup g) {
        return json.createObjectNode()
                .put("economicGroupId", g.id().toString())
                .put("tenantId", g.tenantId().toString())
                .put("code", g.code().value())
                .put("name", g.name())
                .put("referenceCurrency", g.referenceCurrency().getCurrencyCode())
                .put("referenceTimezone", g.referenceTimezone().getId())
                .put("status", g.status().name())
                .put("version", g.version());
    }

    ObjectNode legalEntity(LegalEntityView view, LocalDate effectiveOn) {
        LegalEntity e = view.entity();
        ObjectNode node = json.createObjectNode()
                .put("legalEntityId", e.id().toString())
                .put("tenantId", e.tenantId().toString())
                .put("code", e.code().value())
                .put("legalName", e.legalName())
                .put("tradeName", e.tradeName())
                .put("country", e.country().value())
                .put("subdivision", e.subdivision())
                .put("legalForm", e.legalForm())
                .put("functionalCurrency", e.functionalCurrency().getCurrencyCode())
                .put("timezone", e.timezone().getId())
                .put("defaultLocale", e.defaultLocale().toLanguageTag())
                .put("fiscalYearStartMonth", e.fiscalYearStartMonth())
                .put("status", e.status().name())
                .put(
                        "incorporatedOn",
                        e.incorporatedOn() == null ? null : e.incorporatedOn().toString())
                .put("closedOn", e.closedOn() == null ? null : e.closedOn().toString())
                .put("effectiveOn", effectiveOn.toString())
                .put(
                        "economicGroupId",
                        view.economicGroupId().map(Object::toString).orElse(null));
        ArrayNode groups = node.putArray("groupAssignments");
        view.groupAssignments()
                .forEach(a -> groups.addObject()
                        .put("economicGroupId", a.economicGroupId().toString())
                        .put("validFrom", a.period().validFrom().toString())
                        .put(
                                "validTo",
                                a.period().validTo() == null
                                        ? null
                                        : a.period().validTo().toString()));
        taxIdentifiers(node, view.taxIdentifiers());
        node.put("version", e.version());
        return node;
    }

    ObjectNode establishment(EstablishmentView view, LocalDate effectiveOn) {
        Establishment e = view.establishment();
        ObjectNode node = json.createObjectNode()
                .put("establishmentId", e.id().toString())
                .put("tenantId", e.tenantId().toString())
                .put("legalEntityId", e.legalEntityId().toString())
                .put("code", e.code().value())
                .put("name", e.name())
                .put("type", e.type().name())
                .put("timezone", e.timezone().getId())
                .put("status", e.status().name())
                .put("openedOn", e.openedOn().toString())
                .put("closedOn", e.closedOn() == null ? null : e.closedOn().toString())
                .put("effectiveOn", effectiveOn.toString());
        ObjectNode address = node.putObject("address")
                .put("country", e.address().country().value())
                .put("locality", e.address().locality())
                .put("administrativeArea", e.address().administrativeArea())
                .put("postalCode", e.address().postalCode());
        ArrayNode lines = address.putArray("addressLines");
        e.address().addressLines().forEach(lines::add);
        taxIdentifiers(node, view.taxIdentifiers());
        node.put("version", e.version());
        return node;
    }

    private static void taxIdentifiers(ObjectNode node, List<TaxIdentifierRecord> identifiers) {
        ArrayNode taxes = node.putArray("taxIdentifiers");
        identifiers.forEach(t -> taxes.addObject()
                .put("scheme", t.identifier().scheme().name())
                .put("value", t.identifier().value())
                .put("primary", t.primary())
                .put("validFrom", t.period().validFrom().toString())
                .put(
                        "validTo",
                        t.period().validTo() == null
                                ? null
                                : t.period().validTo().toString()));
    }
}
