package com.meada.whatsapp.profiles.atelie.proposals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Proposta de peça/obra de ateliê (camada 8.14) — espelha atelie_proposals. Order-based com {@code
 * totalCents} materializado (recalculado a cada mutação de item de ORÇAMENTO). {@code projectType}
 * (costura|arte|design) é CAMPO da proposta — o MESMO perfil serve os três tipos. Snapshots de
 * cliente. {@code items} (orçamento) e {@code fittings} (provas/ajustes) hidratados no
 * findById/detalhe (podem vir vazios em listagens leves). DOIS tipos de sub-item no mesmo artefato —
 * não confundir: orçamento entra no total; provas/ajustes NÃO. Espelho do EventProposal.
 */
public record AtelieProposal(
    UUID id,
    UUID contactId,
    UUID artisanId,
    UUID conversationId,
    String customerName,
    String customerPhone,
    String artisanName,
    String projectType,
    String occasion,
    String briefing,
    LocalDate estimatedDate,
    int totalCents,
    String status,
    String notes,
    Instant openedAt,
    Instant closedAt,
    Instant statusUpdatedAt,
    List<AtelieProposalItem> items,
    List<AtelieFitting> fittings) {
}
