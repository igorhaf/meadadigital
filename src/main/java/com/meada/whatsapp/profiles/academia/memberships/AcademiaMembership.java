package com.meada.whatsapp.profiles.academia.memberships;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Matrícula (assinatura) da academia (camada 7.7) — espelha academia_memberships + as aulas
 * (junction). RECORRÊNCIA INDEFINIDA: {@code status} ativa-até-cancelar; {@code endDate} só em
 * cancelada. {@code planName}/{@code planMonthlyCents}/{@code studentName}/{@code studentPhone} são
 * snapshots. {@code classes} são as aulas que o aluno faz (snapshots na junction).
 */
public record AcademiaMembership(
    UUID id,
    UUID planId,
    String planName,
    int planMonthlyCents,
    UUID conversationId,
    UUID contactId,
    String studentName,
    String studentPhone,
    LocalDate startDate,
    LocalDate endDate,
    String status,
    String notes,
    List<MembershipClassEntry> classes,
    Instant createdAt,
    Instant statusUpdatedAt) {
}
