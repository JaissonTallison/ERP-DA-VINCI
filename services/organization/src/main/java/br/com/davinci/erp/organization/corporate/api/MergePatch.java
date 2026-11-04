package br.com.davinci.erp.organization.corporate.api;

import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Leitura de JSON Merge Patch (RFC 7396) com lista fechada de campos: campo desconhecido é erro, não é ignorado. */
final class MergePatch {

    private final JsonNode body;

    MergePatch(JsonNode body, Set<String> allowed) {
        if (!body.isObject()) {
            throw new InvalidValueException("body", "O corpo deve ser um objeto JSON");
        }
        for (String name : body.propertyNames()) {
            if (!allowed.contains(name)) {
                throw new InvalidValueException(name, "Campo não pode ser alterado: " + name);
            }
        }
        this.body = body;
    }

    boolean has(String field) {
        return body.has(field);
    }

    @Nullable
    JsonNode node(String field) {
        return body.get(field);
    }

    /** Texto do campo; nulo se ausente ou explicitamente {@code null}. */
    @Nullable
    String text(String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isString()) {
            throw new InvalidValueException(field, field + " deve ser texto");
        }
        return node.asString();
    }

    @Nullable
    Integer integer(String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isInt()) {
            throw new InvalidValueException(field, field + " deve ser número inteiro");
        }
        return node.asInt();
    }
}
