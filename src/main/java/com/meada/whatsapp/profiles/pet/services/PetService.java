package com.meada.whatsapp.profiles.pet.services;

import java.time.Instant;
import java.util.UUID;

/**
 * Serviço do pet shop (camada 7.8) — espelha pet_services. {@code speciesRestriction} (nullable;
 * 'cao'|'gato'|'outro') valida o fit serviço↔animal no agendamento. {@code durationMinutes} entra
 * como snapshot. Entidade {@code PetService} (record) ≠ {@code PetServiceService} (Spring) — sem
 * colisão de nome.
 */
public record PetService(
    UUID id,
    String name,
    String category,
    int durationMinutes,
    Integer priceCents,
    String speciesRestriction,
    boolean active,
    String description,
    Instant createdAt,
    Instant updatedAt) {
}
