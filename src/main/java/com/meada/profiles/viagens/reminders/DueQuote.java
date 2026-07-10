package com.meada.profiles.viagens.reminders;

import java.util.UUID;

/**
 * Proposta ORÇADA parada há N dias sem resposta, due para o follow-up gentil (onda Viagens,
 * backlog #8). O texto usa destino + total já orçado (valor CRAVADO pela equipe — a IA/job nunca
 * inventa preço).
 */
public record DueQuote(
    UUID proposalId,
    UUID companyId,
    UUID conversationId,
    String customerName,
    String destination,
    int totalCents) {
}
