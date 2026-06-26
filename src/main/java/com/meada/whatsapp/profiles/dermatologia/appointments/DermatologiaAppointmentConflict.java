package com.meada.whatsapp.profiles.dermatologia.appointments;

import java.time.Instant;
import java.util.UUID;

/**
 * Conflito de consulta (camada 8.11): a consulta existente (agendada/confirmada) do MESMO
 * PROFISSIONAL cuja janela temporal sobrepõe a nova. Exposto no 409 conflict_slot.
 */
public record DermatologiaAppointmentConflict(
    UUID existingId,
    String existingPatientName,
    Instant existingStartAt,
    Instant existingEndAt) {
}
