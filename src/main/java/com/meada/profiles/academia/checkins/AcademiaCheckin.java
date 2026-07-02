package com.meada.profiles.academia.checkins;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Check-in / presença de um aluno numa aula (camada 7.7, feature #4) — espelha academia_checkins.
 * {@code checkinDate} é o DIA da presença; {@code checkinAt} o instante exato do registro. UNIQUE
 * (membershipId, classId, checkinDate) impede presença duplicada no mesmo dia. {@code source} = ia|painel.
 */
public record AcademiaCheckin(
    UUID id,
    UUID membershipId,
    UUID classId,
    LocalDate checkinDate,
    Instant checkinAt,
    String source,
    String notes) {
}
