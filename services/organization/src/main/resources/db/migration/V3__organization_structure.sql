-- Etapa 4: tipos de unidade, unidades organizacionais, estruturas e vínculos com vigência (ADR-0019, ADR-0020).

CREATE TABLE unit_type (
    id         uuid        NOT NULL,
    tenant_id  uuid        NOT NULL REFERENCES tenant,
    code       text        NOT NULL,
    name       jsonb       NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version    bigint      NOT NULL,
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT unit_type_code_key UNIQUE (tenant_id, code)
);

-- Quais tipos podem ser pais de um tipo. Tipo sem regras aceita qualquer pai.
CREATE TABLE unit_type_nesting (
    tenant_id      uuid NOT NULL,
    child_type_id  uuid NOT NULL,
    parent_type_id uuid NOT NULL,
    PRIMARY KEY (tenant_id, child_type_id, parent_type_id),
    FOREIGN KEY (tenant_id, child_type_id)  REFERENCES unit_type (tenant_id, id),
    FOREIGN KEY (tenant_id, parent_type_id) REFERENCES unit_type (tenant_id, id)
);

CREATE TABLE organizational_unit (
    id               uuid        NOT NULL,
    tenant_id        uuid        NOT NULL,
    code             text        NOT NULL,
    name             jsonb       NOT NULL,
    unit_type_id     uuid        NOT NULL,
    legal_entity_id  uuid,
    establishment_id uuid,
    status           text        NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'CLOSED')),
    valid_from       date        NOT NULL,
    closed_on        date,
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL,
    version          bigint      NOT NULL,
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT organizational_unit_code_key UNIQUE (tenant_id, code),
    CHECK (closed_on IS NULL OR closed_on >= valid_from),
    CHECK (establishment_id IS NULL OR legal_entity_id IS NOT NULL),
    FOREIGN KEY (tenant_id, unit_type_id)     REFERENCES unit_type (tenant_id, id),
    FOREIGN KEY (tenant_id, legal_entity_id)  REFERENCES legal_entity (tenant_id, id),
    FOREIGN KEY (tenant_id, establishment_id) REFERENCES establishment (tenant_id, id)
);
CREATE INDEX organizational_unit_entity_idx ON organizational_unit (tenant_id, legal_entity_id) WHERE status <> 'CLOSED';

CREATE TABLE organizational_structure (
    id         uuid        NOT NULL,
    tenant_id  uuid        NOT NULL REFERENCES tenant,
    code       text        NOT NULL,
    name       jsonb       NOT NULL,
    purpose    text        NOT NULL CHECK (purpose IN ('MANAGEMENT', 'FUNCTIONAL', 'GEOGRAPHIC', 'CUSTOM')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version    bigint      NOT NULL,
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT organizational_structure_code_key UNIQUE (tenant_id, code)
);

-- Posição da unidade na estrutura, com vigência [valid_from, valid_to): um pai por data, sem sobreposição.
CREATE TABLE structure_link (
    tenant_id      uuid NOT NULL,
    structure_id   uuid NOT NULL,
    unit_id        uuid NOT NULL,
    parent_unit_id uuid,
    valid_from     date NOT NULL,
    valid_to       date,
    CHECK (valid_to IS NULL OR valid_to > valid_from),
    CHECK (parent_unit_id IS DISTINCT FROM unit_id),
    FOREIGN KEY (tenant_id, structure_id)   REFERENCES organizational_structure (tenant_id, id),
    FOREIGN KEY (tenant_id, unit_id)        REFERENCES organizational_unit (tenant_id, id),
    FOREIGN KEY (tenant_id, parent_unit_id) REFERENCES organizational_unit (tenant_id, id),
    CONSTRAINT structure_link_no_overlap
        EXCLUDE USING gist (tenant_id WITH =, structure_id WITH =, unit_id WITH =, daterange(valid_from, valid_to) WITH &&)
);
CREATE INDEX structure_link_children_idx ON structure_link (tenant_id, structure_id, parent_unit_id, valid_from);
CREATE INDEX structure_link_unit_idx ON structure_link (tenant_id, unit_id);

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['unit_type', 'unit_type_nesting', 'organizational_unit', 'organizational_structure',
                             'structure_link']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (tenant_id = current_tenant()) '
                       || 'WITH CHECK (tenant_id = current_tenant())', t);
    END LOOP;
END $$;

GRANT SELECT, INSERT, UPDATE ON unit_type, organizational_unit, organizational_structure, structure_link TO ${appRole};
-- Regras de aninhamento são substituídas por inteiro.
GRANT SELECT, INSERT, DELETE ON unit_type_nesting TO ${appRole};
-- Só para corrigir um movimento no mesmo dia em que o vínculo começava; encerramentos usam UPDATE de valid_to.
GRANT DELETE ON structure_link TO ${appRole};
