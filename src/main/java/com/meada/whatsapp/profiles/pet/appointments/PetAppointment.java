package com.meada.whatsapp.profiles.pet.appointments;

import java.time.Instant;
import java.util.UUID;

/**
 * Agendamento de pet (camada 7.8) — espelha pet_appointments. Snapshots completos: tutor (name/
 * phone), animal (name/species), professional (name), service (name/category/price/duration).
 * {@code conversationId}/{@code contactId} nullable. {@code notes} administrativo.
 */
public record PetAppointment(
    UUID id,
    UUID professionalId,
    String professionalName,
    UUID serviceId,
    String serviceName,
    String serviceCategory,
    UUID animalId,
    String animalName,
    String animalSpecies,
    UUID contactId,
    UUID conversationId,
    String tutorName,
    String tutorPhone,
    Integer priceCents,
    int durationMinutes,
    Instant startAt,
    Instant endAt,
    String status,
    String notes,
    Instant createdAt,
    Instant statusUpdatedAt) {
}
