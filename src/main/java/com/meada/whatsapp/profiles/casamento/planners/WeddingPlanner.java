package com.meada.whatsapp.profiles.casamento.planners;

import java.time.Instant;
import java.util.UUID;

/**
 * Assessor/cerimonialista do tenant casamento (camada 8.7) — espelha wedding_planners.
 * {@code specialty} texto livre ("cerimonial completo", "destination wedding"). Catálogo SIMPLES, sem
 * agenda — atribuição opcional na proposta. Espelho do EventPlanner.
 */
public record WeddingPlanner(
    UUID id,
    String name,
    String specialty,
    boolean active,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
