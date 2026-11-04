package br.com.davinci.erp.organization.reorganization.domain;

import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Passo de uma reorganização, aplicado na data de vigência da reorganização. Os campos usados dependem da operação:
 * {@code MOVE_UNIT} (unitId, parentUnitId; pai nulo = raiz), {@code REMOVE_UNIT} e {@code CLOSE_UNIT} (unitId),
 * {@code RENAME_UNIT} (unitId, name) e {@code REASSIGN_COST_CENTER} (costCenterId, responsibleUnitId).
 */
public record Step(
        Operation operation,
        @Nullable UUID unitId,
        @Nullable UUID parentUnitId,
        @Nullable String name,
        @Nullable UUID costCenterId,
        @Nullable UUID responsibleUnitId) {

    public enum Operation {
        MOVE_UNIT,
        REMOVE_UNIT,
        RENAME_UNIT,
        CLOSE_UNIT,
        REASSIGN_COST_CENTER
    }

    public Step {
        Objects.requireNonNull(operation, "operation");
        switch (operation) {
            case MOVE_UNIT, REMOVE_UNIT, CLOSE_UNIT -> require(unitId, "unitId");
            case RENAME_UNIT -> {
                require(unitId, "unitId");
                if (name == null || name.isBlank() || name.length() > 200) {
                    throw new InvalidValueException("name", "Nome obrigatório, até 200 caracteres");
                }
            }
            case REASSIGN_COST_CENTER -> require(costCenterId, "costCenterId");
        }
    }

    private static void require(@Nullable Object value, String field) {
        if (value == null) {
            throw new InvalidValueException(field, field + " é obrigatório para este passo");
        }
    }
}
