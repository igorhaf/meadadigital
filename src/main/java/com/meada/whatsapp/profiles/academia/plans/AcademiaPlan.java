package com.meada.whatsapp.profiles.academia.plans;

import java.time.Instant;
import java.util.UUID;

/**
 * Plano mensal da academia (camada 7.7) — espelha academia_plans. {@code monthlyCents} é o valor
 * mensal em centavos. Entra como snapshot na matrícula.
 */
public record AcademiaPlan(
    UUID id,
    String name,
    int monthlyCents,
    String description,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {
}
