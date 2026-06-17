package com.meada.whatsapp.profiles.pet.appointments;

import java.time.Instant;
import java.util.UUID;

/**
 * Conflito de agendamento (camada 7.8): o agendamento existente (agendado/confirmado) do MESMO
 * PROFISSIONAL cuja janela temporal sobrepõe o novo. Exposto no 409 conflict_slot.
 */
public record PetAppointmentConflict(
    UUID existingId,
    String existingAnimalName,
    String existingTutorName,
    Instant existingStartAt,
    Instant existingEndAt) {
}
