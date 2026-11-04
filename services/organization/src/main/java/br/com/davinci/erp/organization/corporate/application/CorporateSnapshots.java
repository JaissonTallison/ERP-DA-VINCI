package br.com.davinci.erp.organization.corporate.application;

import br.com.davinci.erp.organization.corporate.domain.EconomicGroup;
import br.com.davinci.erp.organization.corporate.domain.LegalEntity;
import java.time.LocalDate;
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
        ArrayNode taxes = node.putArray("taxIdentifiers");
        view.taxIdentifiers()
                .forEach(t -> taxes.addObject()
                        .put("scheme", t.identifier().scheme().name())
                        .put("value", t.identifier().value())
                        .put("primary", t.primary())
                        .put("validFrom", t.period().validFrom().toString())
                        .put(
                                "validTo",
                                t.period().validTo() == null
                                        ? null
                                        : t.period().validTo().toString()));
        node.put("version", e.version());
        return node;
    }
}
