package br.com.davinci.erp.organization.shared.web;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Corpo opcional das transições de ciclo de vida ({@code activate}, {@code suspend}, {@code close}…). */
public record LifecycleBody(
        @Nullable LocalDate effectiveOn,
        @Size(max = 500) @Nullable String reason) {}
