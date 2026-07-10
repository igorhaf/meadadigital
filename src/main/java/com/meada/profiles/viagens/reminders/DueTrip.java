package com.meada.profiles.viagens.reminders;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Proposta de viagem DUE para um dos disparos temporais da onda Viagens (backlog #2): D-7
 * (checklist), D0 (boa viagem) ou D+2 pós-viagem (NPS). Carrega o mínimo pro job montar o texto e
 * resolver o canal (a conversa da proposta). Espelho do DueFitting do atelie.
 */
public record DueTrip(
    UUID proposalId,
    UUID companyId,
    UUID conversationId,
    String customerName,
    String destination,
    LocalDate startDate,
    LocalDate endDate) {
}
