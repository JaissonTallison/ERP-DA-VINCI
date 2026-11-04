package br.com.davinci.erp.organization.support;

import java.util.concurrent.ThreadLocalRandom;

/** CNPJs numéricos válidos e aleatórios para testes (matriz: filial 0001). */
public final class Cnpjs {

    private static final int[] W1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] W2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private Cnpjs() {}

    public static String random() {
        String base = "%08d0001".formatted(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        String withFirst = base + digit(base, W1);
        return withFirst + digit(withFirst, W2);
    }

    private static int digit(String value, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (value.charAt(i) - '0') * weights[i];
        }
        int r = sum % 11;
        return r < 2 ? 0 : 11 - r;
    }
}
