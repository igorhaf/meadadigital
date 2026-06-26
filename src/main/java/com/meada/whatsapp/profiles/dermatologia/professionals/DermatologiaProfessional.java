package com.meada.whatsapp.profiles.dermatologia.professionals;

import java.time.Instant;
import java.util.UUID;

/**
 * Dermatologista (camada 8.11) — espelha dermatologia_professionals. {@code specialty} texto livre
 * ("dermatologia clínica", "dermatologia estética", "tricologia"); {@code crmRqe} registro
 * profissional (nullable). O conflito de agenda é por profissional.
 */
public record DermatologiaProfessional(
    UUID id,
    String name,
    String specialty,
    String crmRqe,
    boolean active,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
