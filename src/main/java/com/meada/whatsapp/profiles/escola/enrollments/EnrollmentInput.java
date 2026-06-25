package com.meada.whatsapp.profiles.escola.enrollments;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Entrada de uma matrícula via tag {@code <matricula_escola>} (camada 8.19). DOIS MODOS de aluno
 * (espelho do new_animal do pet): {@code studentId} (aluno existente do responsável) OU os campos
 * {@code newStudent*} (cadastra o aluno como sub-entidade da conversa E matricula no mesmo turno).
 * O handler resolve um dos modos antes de chamar o service.
 */
public record EnrollmentInput(
    UUID classId,
    UUID studentId,
    String newStudentName,
    LocalDate newStudentBirthDate,
    String newStudentIntendedGrade,
    String notes) {
}
