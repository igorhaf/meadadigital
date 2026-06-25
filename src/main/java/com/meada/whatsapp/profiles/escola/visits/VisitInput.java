package com.meada.whatsapp.profiles.escola.visits;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Entrada de uma visita via tag {@code <visita_escola>} (camada 8.19). {@code studentId} é opcional
 * (a família pode visitar antes de escolher um aluno/série). O handler resolve o responsável (contato)
 * da conversa antes de chamar o service.
 */
public record VisitInput(
    LocalDate visitDate,
    String period,
    Integer numPeople,
    UUID studentId,
    String notes) {
}
