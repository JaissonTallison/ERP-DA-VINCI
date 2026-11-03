package br.com.davinci.erp.tenantregistry.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.dialect.Dialects;

/**
 * Valida respostas e eventos contra os documentos versionados em contracts/ (fonte da verdade dos contratos), que o
 * Gradle coloca no classpath de testes.
 */
public final class Contracts {

    private static final String OPENAPI = "classpath:http/tenant-registry/v1/openapi.yaml";
    private static final String EVENT_SCHEMA = "classpath:events/tenant-registry/v1/tenant-event.schema.json";

    private static final SchemaRegistry OPENAPI_REGISTRY = SchemaRegistry.withDialect(Dialects.getOpenApi31());
    private static final SchemaRegistry JSON_SCHEMA_REGISTRY =
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    private Contracts() {}

    /** Valida um corpo JSON contra {@code components.schemas.<name>} do OpenAPI. */
    public static void assertMatchesHttpSchema(String name, String json) {
        Schema schema = OPENAPI_REGISTRY.getSchema(SchemaLocation.of(OPENAPI + "#/components/schemas/" + name));
        assertThat(schema.validate(json, InputFormat.JSON))
                .as("resposta deve seguir components.schemas.%s: %s", name, json)
                .isEmpty();
    }

    public static void assertMatchesEventSchema(String json) {
        Schema schema = JSON_SCHEMA_REGISTRY.getSchema(SchemaLocation.of(EVENT_SCHEMA));
        assertThat(schema.validate(json, InputFormat.JSON))
                .as("evento deve seguir tenant-event.schema.json: %s", json)
                .isEmpty();
    }
}
