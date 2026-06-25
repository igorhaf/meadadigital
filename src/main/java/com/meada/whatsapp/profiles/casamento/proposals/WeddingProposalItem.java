package com.meada.whatsapp.profiles.casamento.proposals;

import java.time.Instant;
import java.util.UUID;

/**
 * Item de ORÇAMENTO de uma proposta de casamento (camada 8.7) — espelha wedding_proposal_items.
 * {@code lineTotalCents} é materializado (= quantity * unitPriceCents); o {@code totalCents} da
 * proposta é recalculado na mesma transação a cada mutação de item. ENTRA no total (≠
 * {@link WeddingTimelineItem} e {@link WeddingChecklistTask}, que NÃO entram).
 */
public record WeddingProposalItem(
    UUID id,
    UUID proposalId,
    String description,
    int quantity,
    int unitPriceCents,
    int lineTotalCents,
    Instant createdAt,
    Instant updatedAt) {
}
