package com.meada.whatsapp.profiles.estetica.professionals;

import java.time.Instant;
import java.util.UUID;

/**
 * Profissional do tenant estetica (camada 8.3) — espelha aesthetic_professionals. O conflito de
 * agenda é POR profissional. {@code specialty} texto livre ("facial", "corporal", "laser"). Clone do
 * SalonProfessional.
 */
public record AestheticProfessional(
    UUID id,
    String name,
    String specialty,
    boolean active,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
