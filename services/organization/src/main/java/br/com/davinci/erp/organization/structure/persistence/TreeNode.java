package br.com.davinci.erp.organization.structure.persistence;

import br.com.davinci.erp.organization.shared.domain.LocalizedText;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Unidade posicionada numa estrutura numa data, com profundidade relativa à consulta. */
public record TreeNode(
        UUID unitId,
        String code,
        LocalizedText name,
        String unitTypeCode,
        String status,
        @Nullable UUID parentUnitId,
        int depth,
        boolean hasChildren) {}
