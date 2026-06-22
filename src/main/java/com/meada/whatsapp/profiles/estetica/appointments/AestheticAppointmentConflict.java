package com.meada.whatsapp.profiles.estetica.appointments;

import java.time.Instant;
import java.util.UUID;

/** Detalhes do agendamento existente que conflita com o novo (camada 8.3). Exposto no 409 conflict_slot. */
public record AestheticAppointmentConflict(
    UUID existingId,
    String existingGuestName,
    Instant existingStartAt,
    Instant existingEndAt) {
}
