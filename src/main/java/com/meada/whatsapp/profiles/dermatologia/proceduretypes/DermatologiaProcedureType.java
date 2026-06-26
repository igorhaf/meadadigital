package com.meada.whatsapp.profiles.dermatologia.proceduretypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Tipo de atendimento (camada 8.11, ESCAPADA) — espelha dermatologia_procedure_types. Duração POR
 * TIPO (≠ config fixo do dental/nutri). {@code prepInstructions} é a orientação PRÉ-procedimento
 * entregue VERBATIM pela IA (espelho do body do plano do nutri), NULLABLE. {@code active=false}
 * arquiva. A consulta SNAPSHOTA name+durationMinutes.
 */
public record DermatologiaProcedureType(
    UUID id,
    String name,
    int durationMinutes,
    String prepInstructions,
    boolean active,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
