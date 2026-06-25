package com.meada.whatsapp.profiles.atelie.artisans;

import java.time.Instant;
import java.util.UUID;

/**
 * Artesão/responsável do tenant atelie (camada 8.14) — espelha atelie_artisans. {@code specialty}
 * texto livre ("costura sob medida / alfaiataria", "arte / ilustração"). Catálogo SIMPLES, sem
 * agenda — atribuição opcional na proposta. Espelho do EventPlanner.
 */
public record AtelieArtisan(
    UUID id,
    String name,
    String specialty,
    boolean active,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
