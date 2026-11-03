package br.com.davinci.erp.tenantregistry.application;

import br.com.davinci.erp.platform.kernel.IsolationModel;
import br.com.davinci.erp.platform.kernel.RegionCode;
import br.com.davinci.erp.tenantregistry.domain.TenantSlug;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record RegisterTenantCommand(
        String idempotencyKey, TenantSlug slug, String displayName, RegionCode region, IsolationModel isolation) {

    /** Impressão digital do conteúdo, para detectar a mesma chave reutilizada com outra requisição. */
    String fingerprint() {
        String canonical = String.join("\u001f", slug.value(), displayName.strip(), region.value(), isolation.code());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
