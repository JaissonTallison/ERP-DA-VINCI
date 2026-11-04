-- Etapas 5 a 7: reorganizações, centros de custo e participações societárias (ADR-0019, ADR-0020).

CREATE TABLE reorganization (
    id           uuid        NOT NULL,
    tenant_id    uuid        NOT NULL,
    structure_id uuid        NOT NULL,
    description  text        NOT NULL,
    effective_on date        NOT NULL,
    status       text        NOT NULL CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPLIED', 'REJECTED', 'CANCELLED')),
    created_by   text        NOT NULL,
    submitted_by text,
    decided_by   text,
    decision_note text,
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    version      bigint      NOT NULL,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (id),
    -- quem submete não aprova (segregação de funções)
    CHECK (status <> 'APPLIED' OR (decided_by IS NOT NULL AND decided_by <> submitted_by)),
    FOREIGN KEY (tenant_id, structure_id) REFERENCES organizational_structure (tenant_id, id)
);
CREATE INDEX reorganization_status_idx ON reorganization (tenant_id, status, effective_on);

CREATE TABLE reorganization_step (
    tenant_id         uuid  NOT NULL,
    reorganization_id uuid  NOT NULL,
    position          int   NOT NULL CHECK (position >= 0),
    operation         text  NOT NULL CHECK (operation IN ('MOVE_UNIT', 'REMOVE_UNIT', 'RENAME_UNIT', 'CLOSE_UNIT',
                                                          'REASSIGN_COST_CENTER')),
    payload           jsonb NOT NULL,
    PRIMARY KEY (tenant_id, reorganization_id, position),
    FOREIGN KEY (tenant_id, reorganization_id) REFERENCES reorganization (tenant_id, id)
);

CREATE TABLE cost_center (
    id                  uuid        NOT NULL,
    tenant_id           uuid        NOT NULL,
    legal_entity_id     uuid        NOT NULL,
    code                text        NOT NULL,
    name                text        NOT NULL,
    kind                text        NOT NULL CHECK (kind IN ('POSTABLE', 'GROUP')),
    category            text        NOT NULL,
    responsible_unit_id uuid,
    status              text        NOT NULL CHECK (status IN ('ACTIVE', 'BLOCKED', 'CLOSED')),
    valid_from          date        NOT NULL,
    closed_on           date,
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    version             bigint      NOT NULL,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (id),
    UNIQUE (tenant_id, legal_entity_id, code),
    CHECK (closed_on IS NULL OR closed_on >= valid_from),
    FOREIGN KEY (tenant_id, legal_entity_id) REFERENCES legal_entity (tenant_id, id),
    FOREIGN KEY (tenant_id, responsible_unit_id) REFERENCES organizational_unit (tenant_id, id)
);
CREATE INDEX cost_center_unit_idx ON cost_center (tenant_id, responsible_unit_id);

-- Hierarquia de totalização com vigência; o pai é sempre um centro do tipo GROUP da mesma entidade.
CREATE TABLE cost_center_link (
    tenant_id       uuid NOT NULL,
    legal_entity_id uuid NOT NULL,
    cost_center_id  uuid NOT NULL,
    parent_id       uuid,
    valid_from      date NOT NULL,
    valid_to        date,
    CHECK (valid_to IS NULL OR valid_to > valid_from),
    CHECK (parent_id IS DISTINCT FROM cost_center_id),
    FOREIGN KEY (tenant_id, cost_center_id) REFERENCES cost_center (tenant_id, id),
    FOREIGN KEY (tenant_id, parent_id) REFERENCES cost_center (tenant_id, id),
    EXCLUDE USING gist (tenant_id WITH =, cost_center_id WITH =, daterange(valid_from, valid_to) WITH &&)
);
CREATE INDEX cost_center_link_children_idx ON cost_center_link (tenant_id, legal_entity_id, parent_id, valid_from);

CREATE TABLE external_party (
    id                       uuid        NOT NULL,
    tenant_id                uuid        NOT NULL,
    kind                     text        NOT NULL CHECK (kind IN ('COMPANY', 'PERSON', 'FUND', 'OTHER')),
    name                     text        NOT NULL,
    country                  char(2),
    -- cifrado na aplicação (AES-GCM); nunca volta em claro pela API de listagem
    tax_identifier_encrypted bytea,
    created_at               timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (id)
);

CREATE TABLE ownership_interest (
    id                      uuid          NOT NULL,
    tenant_id               uuid          NOT NULL,
    owned_legal_entity_id   uuid          NOT NULL,
    owner_legal_entity_id   uuid,
    owner_external_party_id uuid,
    capital_percentage      numeric(7, 4) NOT NULL CHECK (capital_percentage > 0 AND capital_percentage <= 100),
    voting_percentage       numeric(7, 4) NOT NULL CHECK (voting_percentage >= 0 AND voting_percentage <= 100),
    share_class             text,
    valid_from              date          NOT NULL,
    valid_to                date,
    created_at              timestamptz   NOT NULL,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (id),
    CHECK (valid_to IS NULL OR valid_to > valid_from),
    CHECK (num_nonnulls(owner_legal_entity_id, owner_external_party_id) = 1),
    CHECK (owner_legal_entity_id IS DISTINCT FROM owned_legal_entity_id),
    FOREIGN KEY (tenant_id, owned_legal_entity_id) REFERENCES legal_entity (tenant_id, id),
    FOREIGN KEY (tenant_id, owner_legal_entity_id) REFERENCES legal_entity (tenant_id, id),
    FOREIGN KEY (tenant_id, owner_external_party_id) REFERENCES external_party (tenant_id, id)
);
-- soma ≤ 100% e ausência de ciclo: OwnershipPolicy, sob trava por tenant (não expressável em CHECK)
CREATE INDEX ownership_owned_idx ON ownership_interest (tenant_id, owned_legal_entity_id, valid_from);
CREATE INDEX ownership_owner_idx ON ownership_interest (tenant_id, owner_legal_entity_id, valid_from);

CREATE INDEX audit_log_period_idx ON audit_log (tenant_id, occurred_at);

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['reorganization', 'reorganization_step', 'cost_center', 'cost_center_link',
                             'external_party', 'ownership_interest']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (tenant_id = current_tenant()) '
                       || 'WITH CHECK (tenant_id = current_tenant())', t);
    END LOOP;
END $$;

GRANT SELECT, INSERT, UPDATE ON reorganization, cost_center, cost_center_link, external_party, ownership_interest
    TO ${appRole};
-- Passos de rascunho são substituídos por inteiro; vínculos de custo seguem a mesma regra dos de estrutura.
GRANT SELECT, INSERT, DELETE ON reorganization_step TO ${appRole};
GRANT DELETE ON cost_center_link TO ${appRole};
