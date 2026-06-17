package com.meada.whatsapp.profiles.academia.memberships;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Entrada da junction matrícula↔aula (camada 7.7) com snapshot da aula. {@code dayOfWeek}
 * 0=domingo..6=sábado.
 */
public record MembershipClassEntry(
    UUID classId,
    String className,
    int dayOfWeek,
    LocalTime startTime,
    int durationMinutes,
    String modality) {
}
