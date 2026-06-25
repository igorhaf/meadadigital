package com.meada.whatsapp.profiles.casamento.proposals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tarefa do CHECKLIST PRÉ-CASAMENTO de uma proposta (camada 8.7) — A ESCAPADA da SM, espelha
 * wedding_checklist_tasks. A 3ª sub-entidade do mesmo artefato: marcos de PREPARAÇÃO que acontecem
 * ANTES do dia (ex.: "enviar convites", "provar o bolo"), cada um com um PRAZO ({@code dueDate}
 * nullable) e estado BINÁRIO {@code done} (pendente/concluída). Lida ORDENADA por due_date asc NULLS
 * LAST, created_at asc (tarefa sem prazo vai ao fim).
 * <ul>
 *   <li>{@code done} BINÁRIO — uma tarefa ou foi feita ou não (não há máquina de status nem enum);
 *   <li>{@code dueDate} é um PRAZO nullable (campo livre, sem conflito de agenda);
 *   <li>{@code doneAt} preenchido ao virar concluída (true), zerado ao voltar a pendente (false).
 * </ul>
 * NÃO entra no {@code totalCents} (≠ {@link WeddingProposalItem}) e NÃO é o cronograma do dia (≠
 * {@link WeddingTimelineItem}). Gerenciada SÓ no painel (sem tag de IA). Espelha o AtelieFitting na
 * FORMA (estado binário + completed_at), mas ordenada por prazo (não por position).
 */
public record WeddingChecklistTask(
    UUID id,
    UUID proposalId,
    String title,
    String description,
    LocalDate dueDate,
    boolean done,
    Instant doneAt) {
}
