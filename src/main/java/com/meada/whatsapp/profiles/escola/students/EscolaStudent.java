package com.meada.whatsapp.profiles.escola.students;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Aluno (camada 8.19) — SUB-ENTIDADE do responsável (contact). Espelha escola_students. O responsável
 * (pai/mãe) é o contact do WhatsApp; um contact tem N alunos. {@code active=false} arquiva sem perder
 * histórico. {@code notes} administrativo (LGPD, sem dado pedagógico/de saúde).
 */
public record EscolaStudent(
    UUID id,
    UUID companyId,
    UUID contactId,
    String name,
    LocalDate birthDate,
    String intendedGrade,
    String notes,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {
}
