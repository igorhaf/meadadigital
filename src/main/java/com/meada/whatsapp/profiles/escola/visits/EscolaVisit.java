package com.meada.whatsapp.profiles.escola.visits;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Visita agendada à escola (camada 8.19, ESCAPADA 2) — espelha escola_visits. Agenda LEVE:
 * {@code visitDate} + {@code period} ('manha'|'tarde'), SEM conflito de capacidade, SEM slot fino.
 * Independe de matrícula e de aluno ({@code studentId} nullable). {@code visitorName}/
 * {@code visitorPhone} são snapshots do responsável.
 */
public record EscolaVisit(
    UUID id,
    UUID companyId,
    UUID conversationId,
    UUID contactId,
    UUID studentId,
    String visitorName,
    String visitorPhone,
    LocalDate visitDate,
    String period,
    Integer numPeople,
    String status,
    String notes,
    Instant createdAt,
    Instant statusUpdatedAt) {
}
