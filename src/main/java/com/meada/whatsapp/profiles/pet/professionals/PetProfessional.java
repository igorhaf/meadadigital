package com.meada.whatsapp.profiles.pet.professionals;

import java.time.Instant;
import java.util.UUID;

/**
 * Profissional do pet shop (camada 7.8) — espelha pet_professionals. {@code specialty} texto livre
 * ("veterinário", "banhista", "tosador"). O conflito de agenda é por profissional.
 */
public record PetProfessional(
    UUID id,
    String name,
    String specialty,
    boolean active,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
