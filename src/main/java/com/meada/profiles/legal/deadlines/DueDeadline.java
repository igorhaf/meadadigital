package com.meada.profiles.legal.deadlines;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Prazo/audiência DUE pro lembrete D-3/D-1 (onda Legal 1). */
public record DueDeadline(
    UUID deadlineId,
    UUID companyId,
    UUID legalClientId,
    String kind,
    String title,
    LocalDate dueDate,
    LocalTime dueTime,
    String location,
    String caseTitle) {
}
