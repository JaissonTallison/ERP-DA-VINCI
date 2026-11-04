package br.com.davinci.erp.organization.shared.domain;

/**
 * CNPJ numérico e alfanumérico (IN RFB 2.229/2024): 12 posições alfanuméricas + 2 dígitos verificadores. O valor de
 * cada caractere é o código ASCII menos 48, o que mantém o cálculo idêntico para o CNPJ só numérico.
 */
final class CnpjValidator {

    private static final int[] FIRST_WEIGHTS = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] SECOND_WEIGHTS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private CnpjValidator() {}

    static boolean isValid(String normalized) {
        if (!normalized.matches("[0-9A-Z]{12}[0-9]{2}")
                || normalized.chars().distinct().count() == 1) {
            return false;
        }
        int first = checkDigit(normalized, FIRST_WEIGHTS);
        int second = checkDigit(normalized, SECOND_WEIGHTS);
        return normalized.charAt(12) - '0' == first && normalized.charAt(13) - '0' == second;
    }

    private static int checkDigit(String value, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (value.charAt(i) - '0') * weights[i];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
