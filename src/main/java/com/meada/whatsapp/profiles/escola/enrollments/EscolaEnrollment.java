package com.meada.whatsapp.profiles.escola.enrollments;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Matrícula (assinatura) da escola (camada 8.19) — espelha escola_enrollments. RECORRÊNCIA
 * INDEFINIDA: {@code status} ativa-até-cancelar; {@code endDate} só em cancelada. Referencia
 * {@code studentId} (o aluno sub-entidade). {@code studentName}/{@code responsibleName}/
 * {@code className}/{@code classGrade}/{@code classShift}/{@code classMonthlyCents} são snapshots.
 */
public record EscolaEnrollment(
    UUID id,
    UUID companyId,
    UUID classId,
    UUID studentId,
    UUID conversationId,
    UUID contactId,
    String studentName,
    String responsibleName,
    String className,
    String classGrade,
    String classShift,
    int classMonthlyCents,
    LocalDate startDate,
    LocalDate endDate,
    String status,
    String notes,
    Instant createdAt,
    Instant statusUpdatedAt) {
}
