package br.com.davinci.erp.organization.corporate.application;

import br.com.davinci.erp.organization.corporate.domain.Establishment;
import br.com.davinci.erp.organization.corporate.domain.TaxIdentifierRecord;
import java.util.List;

/** Estabelecimento com seus identificadores fiscais (com vigência). */
public record EstablishmentView(Establishment establishment, List<TaxIdentifierRecord> taxIdentifiers) {}
