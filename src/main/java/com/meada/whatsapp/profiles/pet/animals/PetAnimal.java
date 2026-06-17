package com.meada.whatsapp.profiles.pet.animals;

import java.time.Instant;
import java.util.UUID;

/**
 * Animal (camada 7.8) — SUB-ENTIDADE do tutor (contact). Espelha pet_animals. {@code species}:
 * 'cao'|'gato'|'outro'; {@code sex}: 'macho'|'femea'|'desconhecido'. {@code active=false} arquiva
 * sem perder histórico. {@code notes} administrativo (LGPD, sem dado clínico).
 */
public record PetAnimal(
    UUID id,
    UUID contactId,
    String name,
    String species,
    String breed,
    String sex,
    Integer birthYear,
    String notes,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {
}
