package br.com.davinci.erp.organization.support;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Corpos de requisição usados nos testes. */
public final class Requests {

    private Requests() {}

    public static String group(String code) {
        return """
                {"code":"%s","name":"Grupo %s","referenceCurrency":"BRL","referenceTimezone":"America/Sao_Paulo"}
                """.formatted(code, code);
    }

    public static String brazilianEntity(
            String code, String cnpj, @Nullable UUID group, String status, String effectiveOn) {
        return """
                {"code":"%s","legalName":"Empresa %s Ltda","country":"BR","subdivision":"BR-SP","legalForm":"LTDA",
                 "functionalCurrency":"BRL","timezone":"America/Sao_Paulo","defaultLocale":"pt-BR",
                 "registeredAddress":{"country":"BR","addressLines":["Av. Paulista, 1000"],"locality":"São Paulo",
                                      "administrativeArea":"SP","postalCode":"01310100"},
                 "taxIdentifiers":[{"scheme":"BR_CNPJ","value":"%s","primary":true}],
                 %s "status":"%s","effectiveOn":"%s"}
                """.formatted(
                code, code, cnpj, group == null ? "" : "\"economicGroupId\":\"" + group + "\",", status, effectiveOn);
    }

    public static String code() {
        return "C"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(java.util.Locale.ROOT);
    }
}
