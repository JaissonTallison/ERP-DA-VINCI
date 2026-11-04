package br.com.davinci.erp.organization.structure.persistence;

import br.com.davinci.erp.organization.shared.domain.LocalizedText;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Nome multilíngue gravado como {@code jsonb}: {@code {"text": "...", "translations": {"en": "..."}}}. */
@Component
class LocalizedTextJson {

    private final JsonMapper json;

    LocalizedTextJson(JsonMapper json) {
        this.json = json;
    }

    String write(LocalizedText text) {
        ObjectNode node = json.createObjectNode().put("text", text.text());
        ObjectNode translations = node.putObject("translations");
        text.translations().forEach(translations::put);
        return json.writeValueAsString(node);
    }

    /** Leitura rápida para listas grandes: o texto já vem extraído pelo SQL; traduções só são lidas se existirem. */
    LocalizedText read(String text, String translationsJson) {
        if ("{}".equals(translationsJson)) {
            return new LocalizedText(text, Map.of());
        }
        Map<String, String> translations = new LinkedHashMap<>();
        json.readTree(translationsJson)
                .properties()
                .forEach(e -> translations.put(e.getKey(), e.getValue().asString()));
        return new LocalizedText(text, translations);
    }

    LocalizedText read(String raw) {
        JsonNode node = json.readTree(raw);
        Map<String, String> translations = new LinkedHashMap<>();
        node.path("translations")
                .properties()
                .forEach(e -> translations.put(e.getKey(), e.getValue().asString()));
        return new LocalizedText(node.path("text").asString(), translations);
    }
}
