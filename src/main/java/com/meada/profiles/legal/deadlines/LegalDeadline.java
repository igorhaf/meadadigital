package com.meada.profiles.legal.deadlines;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Prazo/audiência vinculado ao processo (onda Legal 1, backlog #1). {@code kind} prazo|audiencia;
 * {@code status} pendente|cumprido|perdido (gestão do advogado). Lembrete D-3/D-1 ao cliente
 * vinculado — texto com data/local, sem mérito (trava jurídica).
 */
public record LegalDeadline(
    UUID id,
    UUID caseId,
    String caseTitle,
    String kind,
    String title,
    LocalDate dueDate,
    LocalTime dueTime,
    String location,
    String status,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
