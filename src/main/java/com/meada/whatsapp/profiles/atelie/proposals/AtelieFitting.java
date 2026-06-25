package com.meada.whatsapp.profiles.atelie.proposals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Etapa de PROVA/AJUSTE de uma proposta de ateliê (camada 8.14) — A ESCAPADA da SM, espelha
 * atelie_fittings. Sequência ORDENADA de provas/ajustes da peça sob medida (1ª prova → 2ª prova →
 * ajuste final → entrega), ordenada por {@code position} (asc). NÃO entra no {@code totalCents} da
 * proposta (≠ {@link AtelieProposalItem}, que é preço). Espelha o EventTimelineItem na FORMA, mas:
 * <ul>
 *   <li>{@code status} BINÁRIO ('pendente'/'realizada') — uma prova ou aconteceu ou não;
 *   <li>{@code dueDate} é uma PREVISÃO nullable (campo livre, sem conflito de agenda);
 *   <li>{@code completedAt} preenchido ao virar 'realizada', zerado ao voltar a 'pendente'.
 * </ul>
 * Gerenciada SÓ no painel (sem tag de IA).
 */
public record AtelieFitting(
    UUID id,
    UUID proposalId,
    String title,
    String description,
    LocalDate dueDate,
    String status,
    int position,
    Instant completedAt) {
}
