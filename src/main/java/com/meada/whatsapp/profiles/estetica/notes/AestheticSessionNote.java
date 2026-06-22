package com.meada.whatsapp.profiles.estetica.notes;

import java.time.Instant;
import java.util.UUID;

/**
 * Ficha/evolução textual de uma sessão de estética (camada 8.3) — espelha aesthetic_session_notes.
 * 1:1 com o agendamento. Registro ADMINISTRATIVO-estético (área tratada, parâmetros do aparelho,
 * observações) — NÃO prontuário médico. SEM foto.
 */
public record AestheticSessionNote(
    UUID id,
    UUID appointmentId,
    String treatedArea,
    String deviceParams,
    String observations,
    Instant createdAt,
    Instant updatedAt) {
}
