package br.com.davinci.erp.organization.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.dialect.Dialects;

/** Valida respostas e eventos contra contracts/ (colocado no classpath de testes pelo Gradle). */
public final class Contracts {

    private static final SchemaRegistry OPENAPI = SchemaRegistry.withDialect(Dialects.getOpenApi31());
    private static final SchemaRegistry JSON_SCHEMA =
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    private Contracts() {}

    public static void assertHttp(String schemaName, String json) {
        var schema = OPENAPI.getSchema(
                SchemaLocation.of("classpath:http/organization/v1/openapi.yaml#/components/schemas/" + schemaName));
        assertThat(schema.validate(json, InputFormat.JSON))
                .as("resposta deve seguir components.schemas.%s: %s", schemaName, json)
                .isEmpty();
    }

    public static void assertEvent(String stream, String json) {
        var schema = JSON_SCHEMA.getSchema(
                SchemaLocation.of("classpath:events/organization/v1/" + stream + "-event.schema.json"));
        assertThat(schema.validate(json, InputFormat.JSON))
                .as("evento deve seguir %s-event.schema.json: %s", stream, json)
                .isEmpty();
    }
}
