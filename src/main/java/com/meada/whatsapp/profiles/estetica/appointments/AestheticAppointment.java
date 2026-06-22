package com.meada.whatsapp.profiles.estetica.appointments;

import java.time.Instant;
import java.util.UUID;

/**
 * Agendamento de estética (camada 8.3) — espelha aesthetic_appointments. Clone do SalonAppointment +
 * {@code packageId} (null = avulso) + {@code consumedSession} (true se abateu 1 sessão do pacote).
 * Snapshots professional_name/procedure_name/duration_minutes. {@code sessionNote} hidratado no
 * detalhe (a ficha 1:1, ou null).
 */
public record AestheticAppointment(
    UUID id,
    UUID professionalId,
    String professionalName,
    UUID procedureId,
    String procedureName,
    UUID packageId,
    boolean consumedSession,
    UUID conversationId,
    UUID contactId,
    String guestName,
    String guestPhone,
    Instant startAt,
    Instant endAt,
    int durationMinutes,
    String status,
    String notes,
    Instant createdAt,
    Instant statusUpdatedAt) {
}
