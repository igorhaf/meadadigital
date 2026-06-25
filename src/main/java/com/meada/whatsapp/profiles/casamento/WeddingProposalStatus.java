package com.meada.whatsapp.profiles.casamento;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de uma proposta de casamento (camada 8.7) — clone do
 * {@link com.meada.whatsapp.profiles.eventos.EventProposalStatus}, mesmo funil de aprovação em 2 fases:
 * <pre>
 *   rascunho   → orcada, cancelada
 *   orcada     → aprovada, recusada, cancelada
 *   aprovada   → fechada, cancelada
 *   fechada    → realizada, cancelada
 *   realizada  → (terminal)
 *   recusada   → (terminal)
 *   cancelada  → (terminal)
 * </pre>
 * (rascunho = proposta aberta sem orçamento; orcada = aguardando aprovação dos noivos; aprovada =
 * noivos aceitaram; fechada = "contrato" fechado/sinal combinado fora do app; realizada = o casamento
 * aconteceu.)
 *
 * <p>Transição inválida → 409 invalid_status_transition no controller. A passagem para {@code orcada}
 * exige {@code total_cents > 0} (validada no service → 400 empty_budget). {@link #itemsLocked()} a
 * partir de 'fechada' congela os TRÊS sub-itens (orçamento E cronograma E checklist).
 *
 * <p>Espelhado 1:1 por {@code frontend/profiles/casamento/wedding-proposal-status.ts}
 * (WeddingProposalStatusParityTest garante a paridade Java↔TS).
 */
public enum WeddingProposalStatus {
    RASCUNHO("rascunho", "Rascunho"),
    ORCADA("orcada", "Orçada"),
    APROVADA("aprovada", "Aprovada"),
    RECUSADA("recusada", "Recusada"),
    FECHADA("fechada", "Fechada"),
    REALIZADA("realizada", "Realizada"),
    CANCELADA("cancelada", "Cancelada");

    private final String id;
    private final String label;

    WeddingProposalStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<WeddingProposalStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status. */
    public Set<WeddingProposalStatus> allowedNext() {
        return switch (this) {
            case RASCUNHO -> Set.of(ORCADA, CANCELADA);
            case ORCADA -> Set.of(APROVADA, RECUSADA, CANCELADA);
            case APROVADA -> Set.of(FECHADA, CANCELADA);
            case FECHADA -> Set.of(REALIZADA, CANCELADA);
            case REALIZADA, RECUSADA, CANCELADA -> Set.of();
        };
    }

    public boolean canTransitionTo(WeddingProposalStatus next) {
        return allowedNext().contains(next);
    }

    /** Estado terminal — proposta encerrada (preenche closed_at). */
    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }

    /**
     * Estados em que os TRÊS sub-itens (orçamento, cronograma E checklist) NÃO podem mais ser mutados
     * (trava de estado a partir de 'fechada'). Mutar sub-item nesses estados → 409 proposal_locked.
     * Antes disso (rascunho/orcada/aprovada) a equipe ainda ajusta tudo.
     */
    public boolean itemsLocked() {
        return switch (this) {
            case FECHADA, REALIZADA, RECUSADA, CANCELADA -> true;
            case RASCUNHO, ORCADA, APROVADA -> false;
        };
    }

    /**
     * Texto fixo da notificação outbound ao ENTRAR neste status. null = não notifica.
     * {@code orcada} (com total), {@code aprovada}, {@code fechada} e {@code recusada} avisam os
     * noivos; {@code rascunho}/{@code realizada}/{@code cancelada} são silenciosos. Texto defensivo,
     * SEM promessa de "casamento perfeito". Ver {@link #notificationText(String, String)} para o caso
     * orcada (com total + estilo).
     */
    public String notificationText() {
        return switch (this) {
            case APROVADA -> "Que alegria receber sua aprovação! Vamos preparar o contrato e os próximos passos do grande dia.";
            case FECHADA -> "Tudo certo, seu casamento está confirmado com a gente! Em breve alinhamos os detalhes finais.";
            case RECUSADA -> "Tudo bem, registramos que a proposta não foi adiante. Seguimos à disposição para o que precisar.";
            case ORCADA, RASCUNHO, REALIZADA, CANCELADA -> null;
        };
    }

    /**
     * Texto da notificação de ORÇAMENTO (status orcada), com total formatado + estilo do casamento.
     * Para os demais status, devolve {@link #notificationText()}.
     */
    public String notificationText(String styleLabel, String totalLabel) {
        if (this == ORCADA) {
            return "Orçamento do seu casamento " + styleLabel + ": " + totalLabel
                + ". Posso seguir? Responda com sim para aprovar ou não para recusar.";
        }
        return notificationText();
    }
}
