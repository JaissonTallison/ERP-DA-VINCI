package br.com.davinci.erp.organization.reorganization.domain;

import br.com.davinci.erp.organization.shared.domain.ConflictException;
import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import br.com.davinci.erp.platform.kernel.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Reorganização de uma estrutura: um conjunto de passos aplicado inteiro, numa data, depois de aprovado por alguém
 * diferente de quem submeteu. Imutável: cada mudança devolve nova versão.
 */
public record Reorganization(
        UUID id,
        TenantId tenantId,
        UUID structureId,
        String description,
        LocalDate effectiveOn,
        ReorganizationStatus status,
        String createdBy,
        @Nullable String submittedBy,
        @Nullable String decidedBy,
        @Nullable String decisionNote,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public static final int MAX_STEPS = 1000;

    public Reorganization {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(structureId, "structureId");
        if (description == null || description.isBlank() || description.length() > 500) {
            throw new InvalidValueException("description", "Descrição obrigatória, até 500 caracteres");
        }
        Objects.requireNonNull(effectiveOn, "effectiveOn");
        Objects.requireNonNull(status, "status");
    }

    public static Reorganization draft(
            UUID id, TenantId tenant, UUID structureId, String description, LocalDate on, String actor, Instant now) {
        return new Reorganization(
                id,
                tenant,
                structureId,
                description,
                on,
                ReorganizationStatus.DRAFT,
                actor,
                null,
                null,
                null,
                now,
                now,
                0);
    }

    public Reorganization edit(String newDescription, LocalDate on, Instant now) {
        requireStatus(ReorganizationStatus.DRAFT, "alterar");
        return with(newDescription, on, status, submittedBy, decidedBy, decisionNote, now);
    }

    /** Marca alteração nos passos (nova versão). */
    public Reorganization touch(Instant now) {
        requireStatus(ReorganizationStatus.DRAFT, "alterar passos de");
        return with(description, effectiveOn, status, submittedBy, decidedBy, decisionNote, now);
    }

    public Reorganization submit(String actor, int steps, Instant now) {
        requireStatus(ReorganizationStatus.DRAFT, "submeter");
        if (steps == 0) {
            throw new ConflictException("empty-reorganization", "A reorganização não tem passos");
        }
        return with(description, effectiveOn, ReorganizationStatus.PENDING_APPROVAL, actor, null, null, now);
    }

    public Reorganization approve(String actor, @Nullable String note, Instant now) {
        requireStatus(ReorganizationStatus.PENDING_APPROVAL, "aprovar");
        if (actor.equals(submittedBy)) {
            throw new ConflictException("segregation-of-duties", "Quem submeteu a reorganização não pode aprová-la");
        }
        return with(description, effectiveOn, ReorganizationStatus.APPLIED, submittedBy, actor, note, now);
    }

    public Reorganization reject(String actor, @Nullable String note, Instant now) {
        requireStatus(ReorganizationStatus.PENDING_APPROVAL, "rejeitar");
        return with(description, effectiveOn, ReorganizationStatus.REJECTED, submittedBy, actor, note, now);
    }

    public Reorganization cancel(String actor, Instant now) {
        if (status != ReorganizationStatus.DRAFT && status != ReorganizationStatus.PENDING_APPROVAL) {
            throw new ConflictException("invalid-transition", "Só rascunho ou pendente pode ser cancelada");
        }
        return with(description, effectiveOn, ReorganizationStatus.CANCELLED, submittedBy, actor, null, now);
    }

    private void requireStatus(ReorganizationStatus expected, String action) {
        if (status != expected) {
            throw new ConflictException(
                    "invalid-transition", "Não é possível " + action + " uma reorganização em " + status);
        }
    }

    private Reorganization with(
            String d,
            LocalDate on,
            ReorganizationStatus s,
            @Nullable String submitted,
            @Nullable String decided,
            @Nullable String note,
            Instant now) {
        return new Reorganization(
                id, tenantId, structureId, d, on, s, createdBy, submitted, decided, note, createdAt, now, version + 1);
    }
}
