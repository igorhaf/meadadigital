package com.meada.whatsapp.profiles.dermatologia.appointments;

import java.time.Instant;
import java.util.UUID;

/**
 * Consulta de dermatologia (camada 8.11) — espelha dermatologia_appointments. Snapshots de
 * paciente/profissional/tipo+duração. {@code procedureTypeId} referencia o tipo;
 * {@code procedureTypeName}+{@code durationMinutes} são SNAPSHOTS. {@code endAt} materializado no
 * INSERT (start_at + duration_minutes).
 */
public record DermatologiaAppointment(
    UUID id,
    UUID professionalId,
    String professionalName,
    UUID patientId,
    String patientName,
    String patientPhone,
    UUID procedureTypeId,
    String procedureTypeName,
    UUID contactId,
    UUID conversationId,
    int durationMinutes,
    Instant startAt,
    Instant endAt,
    String status,
    String notes,
    Instant createdAt,
    Instant statusUpdatedAt) {
}
