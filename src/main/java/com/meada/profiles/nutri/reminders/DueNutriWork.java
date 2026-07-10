package com.meada.profiles.nutri.reminders;

import java.time.Instant;
import java.util.UUID;

/**
 * Item DUE dos disparos da nutri (onda 1, backlog #1/#2/#5): consulta de amanhã a lembrar,
 * consulta confirmada vencida a auto-realizar, ou paciente inativo a reengajar (aí {@code id}
 * é o patientId e {@code startAt} a última realizada).
 */
public record DueNutriWork(
    UUID id,
    UUID companyId,
    UUID conversationId,
    String personName,
    String professionalName,
    Instant startAt) {
}
