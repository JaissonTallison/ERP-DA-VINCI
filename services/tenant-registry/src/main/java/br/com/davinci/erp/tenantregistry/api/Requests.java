package br.com.davinci.erp.tenantregistry.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Corpos de requisição v1. Formatos detalhados são validados pelos tipos do domínio. */
final class Requests {

    private Requests() {}

    record RegisterTenant(
            @NotBlank @Nullable String slug,
            @NotBlank @Size(max = 120) @Nullable String displayName,
            @NotBlank @Nullable String region,
            @NotBlank @Nullable String isolation) {}

    record SuspendTenant(
            @NotBlank @Size(max = 500) @Nullable String reason) {}

    record RegisterCell(
            @NotBlank @Nullable String region,
            @NotBlank @Nullable String isolation,
            @NotBlank @Nullable String name,
            @NotNull @Min(1) @Max(100_000) @Nullable Integer capacity) {}
}
