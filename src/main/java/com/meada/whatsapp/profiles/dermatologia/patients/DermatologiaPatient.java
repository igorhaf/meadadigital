package com.meada.whatsapp.profiles.dermatologia.patients;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Paciente (camada 8.11) — SUB-ENTIDADE do contact. Espelha dermatologia_patients. {@code notes} é
 * texto livre ADMINISTRATIVO (sem prontuário/diagnóstico — LGPD). {@code active=false} arquiva sem
 * perder histórico.
 */
public record DermatologiaPatient(
    UUID id,
    UUID contactId,
    String name,
    LocalDate birthDate,
    String notes,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {
}
