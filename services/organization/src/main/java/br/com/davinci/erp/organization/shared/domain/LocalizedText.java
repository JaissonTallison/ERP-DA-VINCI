package br.com.davinci.erp.organization.shared.domain;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Texto com versão padrão e traduções por idioma (BCP 47). Nomes legais não usam isto: não se traduzem. */
public record LocalizedText(String text, Map<String, String> translations) {

    public LocalizedText {
        Objects.requireNonNull(text, "text");
        text = text.strip();
        if (text.isEmpty() || text.length() > 200) {
            throw new InvalidValueException("name", "Nome deve ter de 1 a 200 caracteres");
        }
        Map<String, String> normalized = new TreeMap<>();
        for (var entry : Objects.requireNonNull(translations, "translations").entrySet()) {
            String tag =
                    RegionalSettings.locale("nameTranslations", entry.getKey()).toLanguageTag();
            String value = entry.getValue() == null ? "" : entry.getValue().strip();
            if (value.isEmpty() || value.length() > 200) {
                throw new InvalidValueException("nameTranslations", "Tradução vazia ou longa demais para " + tag);
            }
            normalized.put(tag, value);
        }
        translations = Map.copyOf(normalized);
    }

    public static LocalizedText of(String text) {
        return new LocalizedText(text, Map.of());
    }
}
