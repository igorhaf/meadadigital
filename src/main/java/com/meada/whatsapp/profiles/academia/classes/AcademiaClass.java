package com.meada.whatsapp.profiles.academia.classes;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Aula semanal recorrente da academia (camada 7.7) — espelha academia_classes. {@code dayOfWeek}
 * 0=domingo..6=sábado. {@code capacity} = máximo de matrículas ativas. Entra como snapshot na
 * junction da matrícula. {@code instructor} texto livre opcional (não é entidade).
 */
public record AcademiaClass(
    UUID id,
    String name,
    String modality,
    int dayOfWeek,
    LocalTime startTime,
    int durationMinutes,
    int capacity,
    String instructor,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {
}
