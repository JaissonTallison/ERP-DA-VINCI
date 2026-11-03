package br.com.davinci.erp.identity.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.UUID;

/** Eventos do Tenant Registry para testes, validados contra o contrato publicado pelo produtor. */
public final class TenantEvents {

    private static final Schema SCHEMA = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(SchemaLocation.of("classpath:events/tenant-registry/v1/tenant-event.schema.json"));

    private TenantEvents() {}

    public static String event(UUID tenantId, String slug, String type, String status, long version) {
        String id = UUID.randomUUID().toString();
        String json = """
                {"specversion":"1.0","id":"%s","source":"/control-plane/tenant-registry",
                 "type":"br.com.davinci.tenantregistry.tenant.%s.v1","time":"2026-11-03T12:00:00Z",
                 "datacontenttype":"application/json",
                 "dataschema":"urn:davinci:contracts:events:tenant-registry:v1:tenant-event",
                 "subject":"%s","tenantid":"%s","cellid":"br-1-pooled-01","correlationid":"teste",
                 "data":{"tenantId":"%s","slug":"%s","displayName":"Empresa %s","region":"br-1",
                         "isolation":"pooled","cellId":"br-1-pooled-01","status":"%s","statusReason":null,
                         "version":%d}}
                """.formatted(id, type, tenantId, tenantId, tenantId, slug, slug, status, version);
        assertThat(SCHEMA.validate(json, InputFormat.JSON))
                .as("fixture deve seguir o contrato")
                .isEmpty();
        return json;
    }
}
