package com.meada.profiles.cursos.enrollments;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Matrícula (assinatura) do tenant cursos (camada 8.20 / perfil cursos) — espelha cursos_enrollments;
 * clone do AcademiaMembership (camada 7.7), mas a matrícula é num ÚNICO curso (course_id direto, sem
 * junction de aulas). RECORRÊNCIA INDEFINIDA: {@code status} ativa-até-concluir/cancelar; {@code
 * endDate} em concluida/cancelada. {@code courseTitle}/{@code courseMonthlyCents}/{@code studentName}/
 * {@code studentPhone} são snapshots.
 */
public record CursosEnrollment(
    UUID id,
    UUID courseId,
    String courseTitle,
    int courseMonthlyCents,
    int discountCents,
    String couponCode,
    UUID conversationId,
    UUID contactId,
    String studentName,
    String studentPhone,
    LocalDate startDate,
    LocalDate endDate,
    String status,
    String notes,
    Instant createdAt,
    Instant statusUpdatedAt) {
}
