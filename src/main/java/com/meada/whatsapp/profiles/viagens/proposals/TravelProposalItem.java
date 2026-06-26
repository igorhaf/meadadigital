package com.meada.whatsapp.profiles.viagens.proposals;

import java.time.Instant;
import java.util.UUID;

/**
 * Item de COTAÇÃO de uma proposta de viagem (camada 8.18 / perfil viagens) — espelha
 * travel_proposal_items. {@code category} aereo/hospedagem/traslado/passeio/outro (texto livre com
 * CHECK na migration). {@code lineTotalCents} é materializado (= quantity * unitPriceCents); o
 * {@code totalCents} da proposta é recalculado na mesma transação a cada mutação de item. ENTRA no
 * total (≠ {@link TravelItineraryDay}, que é roteiro e NÃO entra). Espelho do EventProposalItem
 * (chassi eventos 8.2) + category.
 */
public record TravelProposalItem(
    UUID id,
    UUID proposalId,
    String category,
    String description,
    int quantity,
    int unitPriceCents,
    int lineTotalCents,
    Instant createdAt,
    Instant updatedAt) {
}
