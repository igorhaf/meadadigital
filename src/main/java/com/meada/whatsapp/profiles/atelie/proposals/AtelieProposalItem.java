package com.meada.whatsapp.profiles.atelie.proposals;

import java.time.Instant;
import java.util.UUID;

/**
 * Item de ORÇAMENTO de uma proposta de ateliê (camada 8.14) — espelha atelie_proposal_items.
 * {@code lineTotalCents} é materializado (= quantity * unitPriceCents); o {@code totalCents} da
 * proposta é recalculado na mesma transação a cada mutação de item. ENTRA no total (≠
 * {@link AtelieFitting}, que é prova/ajuste e NÃO entra). Espelho do EventProposalItem.
 */
public record AtelieProposalItem(
    UUID id,
    UUID proposalId,
    String description,
    int quantity,
    int unitPriceCents,
    int lineTotalCents,
    Instant createdAt,
    Instant updatedAt) {
}
