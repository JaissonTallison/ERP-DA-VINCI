package br.com.davinci.erp.organization.corporate.application;

import br.com.davinci.erp.organization.corporate.domain.GroupAssignment;
import br.com.davinci.erp.organization.corporate.domain.GroupAssignments;
import br.com.davinci.erp.organization.corporate.domain.LegalEntity;
import br.com.davinci.erp.organization.corporate.domain.TaxIdentifierRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Entidade jurídica com seu histórico de grupos e identificadores fiscais, lida numa data ({@code asOf}). */
public record LegalEntityView(
        LegalEntity entity,
        List<GroupAssignment> groupAssignments,
        List<TaxIdentifierRecord> taxIdentifiers,
        LocalDate asOf) {

    public Optional<UUID> economicGroupId() {
        return GroupAssignments.groupAt(groupAssignments, asOf);
    }
}
