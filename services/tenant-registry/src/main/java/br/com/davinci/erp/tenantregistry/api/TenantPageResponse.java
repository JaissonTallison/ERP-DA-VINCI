package br.com.davinci.erp.tenantregistry.api;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record TenantPageResponse(
        List<TenantResponse> items, @Nullable String nextCursor) {}
