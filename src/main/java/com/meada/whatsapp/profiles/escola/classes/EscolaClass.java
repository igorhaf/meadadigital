package com.meada.whatsapp.profiles.escola.classes;

import java.time.Instant;
import java.util.UUID;

/**
 * Turma da escola (camada 8.19) — espelha escola_classes. Combina o "plano" da academia (mensalidade)
 * com campos de turma (série/turno/capacity/ano). {@code shift}: 'manha'|'tarde'|'integral'.
 * {@code capacity} = máx. de matrículas não-canceladas. {@code monthlyCents} entra como SNAPSHOT na
 * matrícula. {@code grade} = série/ano texto livre. {@code active=false} arquiva.
 */
public record EscolaClass(
    UUID id,
    UUID companyId,
    String name,
    String grade,
    String shift,
    int capacity,
    int monthlyCents,
    Integer year,
    String description,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {
}
