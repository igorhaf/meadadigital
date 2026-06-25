package com.meada.whatsapp.profiles.casamento.proposals;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Marco de CRONOGRAMA do dia do casamento (camada 8.7) — espelha wedding_timeline_items. Roteiro
 * ORDENADO por {@code startTime} (ex.: 16:00 chegada / 17:00 cerimônia / 19:00 jantar / 22:00 festa).
 * NÃO entra no {@code totalCents} da proposta (≠ {@link WeddingProposalItem}, que é preço). É o "dia
 * do casamento" organizacional. Espelho do EventTimelineItem.
 */
public record WeddingTimelineItem(
    UUID id,
    UUID proposalId,
    LocalTime startTime,
    String title,
    String description,
    Instant createdAt,
    Instant updatedAt) {
}
